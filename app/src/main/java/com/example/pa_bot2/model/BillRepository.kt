package com.example.pa_bot2.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object BillRepository {
    private var billDao: BillDao? = null
    private var appContext: Context? = null
    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills: StateFlow<List<Bill>> = _bills.asStateFlow()

    fun initialize(context: Context, dao: BillDao) {
        appContext = context.applicationContext
        billDao = dao
    }

    suspend fun loadBills() {
        billDao?.getAllBills()?.collect {
            _bills.value = it
        }
    }

    suspend fun addBill(bill: Bill) {
        val billWithSavedImage = persistImage(bill)
        billDao?.insertBill(billWithSavedImage)
    }

    suspend fun getBill(id: String): Bill? {
        return billDao?.getBillById(id)
    }
    
    suspend fun updateBill(updatedBill: Bill) {
        val billWithSavedImage = persistImage(updatedBill)
        billDao?.updateBill(billWithSavedImage)
    }

    suspend fun deleteBill(id: String) {
        val bill = getBill(id)
        bill?.imageUri?.let { uriString ->
            try {
                val file = File(Uri.parse(uriString).path ?: "")
                if (file.exists() && file.absolutePath.contains("bill_images")) {
                    file.delete()
                }
            } catch (e: Exception) {}
        }
        billDao?.deleteBillById(id)
    }

    private fun persistImage(bill: Bill): Bill {
        val uriString = bill.imageUri ?: return bill
        val context = appContext ?: return bill
        
        try {
            val sourceUri = Uri.parse(uriString)
            // If it's already in our permanent storage, don't copy again
            if (uriString.contains("bill_images")) return bill

            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return bill
            val imagesDir = File(context.filesDir, "bill_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val fileName = "bill_${bill.id}.jpg"
            val destinationFile = File(imagesDir, fileName)

            inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            return bill.copy(imageUri = Uri.fromFile(destinationFile).toString())
        } catch (e: Exception) {
            android.util.Log.e("BillRepository", "Failed to persist image", e)
            return bill
        }
    }
}
