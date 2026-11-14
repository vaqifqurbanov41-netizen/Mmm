package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FarmerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val phone: String = "",
    val place: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FarmField(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val crop: String = "",
    val sizeHa: Double = 0.0,
    val widthM: Double = 0.0,
    val lengthM: Double = 0.0,
    val location: String = "",
    val coords: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class EquipmentItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val model: String = "",
    val plateCode: String = "",
    val inspectionDate: String = "",
    val insuranceDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FinanceEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "expense", // "income" or "expense"
    val date: String = "",
    val fieldName: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class HarvestEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val date: String = "",
    val crop: String = "",
    val quantityKg: Double = 0.0,
    val pricePerKg: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SubsidyDeclaration(
    @DocumentId val id: String = "",
    val userId: String = "",
    val date: String = "",
    val title: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CustomPesticide(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val maxRate: Double = 0.0,
    val unit: String = "L/ha",
    val quarantineDays: Long = 0L,
    val safetyClass: String = "",
    val target: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CustomLink(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val url: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
