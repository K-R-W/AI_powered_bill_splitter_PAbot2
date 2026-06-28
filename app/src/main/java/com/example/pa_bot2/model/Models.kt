package com.example.pa_bot2.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class Participant(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorIndex: Int = 0,
    val splitwiseId: Long? = null
)

@Serializable
data class BillItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val assignedParticipantIds: List<String> = emptyList(),
    val participantShares: Map<String, Double> = emptyMap(),
    val isProportionalSplit: Boolean = false
)

@Serializable
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: Long = System.currentTimeMillis(),
    val items: List<BillItem> = emptyList(),
    val participants: List<Participant> = emptyList(),
    val payerAmounts: Map<String, Double> = emptyMap(),
    val imageUri: String? = null,
    val splitwiseGroupId: String? = null,
    val currencyCode: String = "INR"
)

@Serializable
data class SplitwiseGroup(
    val id: Long,
    val name: String,
    val members: List<SplitwiseMember> = emptyList()
)

@Serializable
data class SplitwiseMember(
    val id: Long,
    val first_name: String,
    val last_name: String? = null,
    val email: String? = null,
    val picture: SplitwisePicture? = null
)

@Serializable
data class SplitwisePicture(
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null
)

@Serializable
data class SplitwiseUser(
    val id: Long,
    val first_name: String,
    val last_name: String? = null,
    val email: String,
    val picture: SplitwisePicture? = null
)

class Converters {
    @TypeConverter
    fun fromBillItemList(value: List<BillItem>): String = Json.encodeToString(value)

    @TypeConverter
    fun toBillItemList(value: String): List<BillItem> = Json.decodeFromString(value)

    @TypeConverter
    fun fromParticipantList(value: List<Participant>): String = Json.encodeToString(value)

    @TypeConverter
    fun toParticipantList(value: String): List<Participant> = Json.decodeFromString(value)

    @TypeConverter
    fun fromPayerAmounts(value: Map<String, Double>): String = Json.encodeToString(value)

    @TypeConverter
    fun toPayerAmounts(value: String): Map<String, Double> = Json.decodeFromString(value)
}
