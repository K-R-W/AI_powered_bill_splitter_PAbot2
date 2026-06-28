package com.example.pa_bot2.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.pa_bot2.model.BillItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max

class GeminiBillExtractor(private val context: Context, private val accessToken: String) : BillExtractor {

    // Generous timeouts: uploading an image and generating content can take a few seconds.
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(imageUri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        Log.d("GeminiBillExtractor", "Starting extraction for URI: $imageUri")
        val original = context.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw Exception("Failed to decode bitmap from URI")

        // Receipts are text-heavy but don't need full sensor resolution; downscaling keeps
        // uploads small and fast without hurting OCR accuracy.
        val bitmap = downscale(original, MAX_IMAGE_DIMENSION)
        val base64Image = bitmapToBase64(bitmap)
        Log.d("GeminiBillExtractor", "Image ${bitmap.width}x${bitmap.height}, base64 length ${base64Image.length}")

        val prompt = """
            You are reading a photo of a purchase receipt or bill.
            Extract every line item with its name and price. Include additional charges that
            appear on the bill — tax, service charge, tip — as their own line items.
            Also provide a short, human-friendly title for the bill, usually the merchant or
            place name (e.g. "Starbucks", "Big Bazaar", "Dinner at Joey's").
            Prices must be plain numbers in the receipt's currency, with no symbols or grouping.
        """.trimIndent()

        // responseSchema + JSON mime type makes the model return strict JSON (no markdown
        // fences, no prose), so we never have to clean up the response text.
        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("title") { put("type", "STRING") }
                        putJsonObject("items") {
                            put("type", "ARRAY")
                            putJsonObject("items") {
                                put("type", "OBJECT")
                                putJsonObject("properties") {
                                    putJsonObject("name") { put("type", "STRING") }
                                    putJsonObject("price") { put("type", "NUMBER") }
                                }
                                putJsonArray("required") {
                                    add("name")
                                    add("price")
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("title")
                        add("items")
                    }
                }
            }
        }.toString()

        Log.d("GeminiBillExtractor", "Sending request to Gemini API...")
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            Log.d("GeminiBillExtractor", "Received response. Code: ${response.code}")
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e("GeminiBillExtractor", "API Error (${response.code}): $errorBody")
                if (response.code == 401 || response.code == 403) {
                    throw AuthenticationException("Gemini Auth Failed: $errorBody", Exception())
                }
                throw Exception("API Error (${response.code}): $errorBody")
            }

            val responseBody = response.body?.string() ?: throw Exception("Response body is null")
            val textResponse = json.parseToJsonElement(responseBody).jsonObject["candidates"]
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content ?: throw Exception("Failed to extract text content from response JSON")

            return@withContext try {
                val resultObj = json.parseToJsonElement(textResponse.trim()).jsonObject
                val title = resultObj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                val itemsArray = resultObj["items"]?.jsonArray ?: JsonArray(emptyList())
                val items = itemsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val price = obj["price"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    if (name.isEmpty()) null else BillItem(name = name, price = price)
                }
                ExtractionResult(items = items, suggestedTitle = title)
            } catch (e: Exception) {
                Log.e("GeminiBillExtractor", "Failed to parse JSON: $textResponse", e)
                throw Exception("Failed to parse bill data")
            }
        }
    }

    override suspend fun extractItems(imageUri: Uri): List<BillItem> = extract(imageUri).items

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    class AuthenticationException(message: String, cause: Throwable) : Exception(message, cause)

    companion object {
        private const val MODEL = "gemini-flash-latest"
        private const val MAX_IMAGE_DIMENSION = 1536
    }
}
