package com.example.data.repository

import android.content.Context
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.firebase.resolveFirestoreDatabaseId
import com.example.data.model.ADMIN_EMAIL
import com.example.data.model.AdminContentItem
import com.example.data.model.CustomLink
import com.example.data.model.CustomPesticide
import com.example.data.model.EquipmentItem
import com.example.data.model.FarmField
import com.example.data.model.FarmerProfile
import com.example.data.model.FinanceEntry
import com.example.data.model.HarvestEntry
import com.example.data.model.SubsidyDeclaration
import com.example.data.model.isAdminEmail
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FarmRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            resolveFirestoreDatabaseId(context.applicationContext)
        ),
        Firebase.auth,
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun newDocId(): String = UUID.randomUUID().toString()

    private fun <T> observeUserSubcollection(
        collectionName: String,
        clazz: Class<T>,
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/$collectionName"
        emitAll(
            db.collection("users").document(uid).collection(collectionName)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        clazz,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProfile(): Flow<FarmerProfile?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            FarmerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveProfile(fullName: String, phone: String, place: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "fullName" to fullName.trim().take(120),
                        "phone" to phone.trim().take(40),
                        "place" to place.trim().take(120),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "fullName" to fullName.trim().take(120),
                        "phone" to phone.trim().take(40),
                        "place" to place.trim().take(120),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // Fields
    fun observeFields(): Flow<List<FarmField>> =
        observeUserSubcollection("fields", FarmField::class.java)

    suspend fun addField(
        name: String,
        crop: String,
        sizeHa: Double,
        widthM: Double,
        lengthM: Double,
        location: String,
        coords: List<String>,
    ): String {
        val uid = requireUserId()
        val docId = newDocId()
        val docRef = db.collection("users").document(uid).collection("fields").document(docId)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(120),
            "crop" to crop.trim().take(100),
            "sizeHa" to sizeHa,
            "widthM" to widthM,
            "lengthM" to lengthM,
            "location" to location.trim().take(150),
            "coords" to coords.take(100),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteField(fieldId: String) = deleteSubDoc("fields", fieldId)

    // Equipment
    fun observeEquipment(): Flow<List<EquipmentItem>> =
        observeUserSubcollection("equipment", EquipmentItem::class.java)

    suspend fun saveEquipment(
        existingItem: EquipmentItem?,
        name: String,
        model: String,
        plateCode: String,
        inspectionDate: String,
        insuranceDate: String,
    ): String {
        val uid = requireUserId()
        if (existingItem != null && existingItem.id.isNotBlank()) {
            val docRef = db.collection("users").document(uid)
                .collection("equipment").document(existingItem.id)
            try {
                docRef.update(
                    mapOf(
                        "name" to name.trim().take(120),
                        "model" to model.trim().take(100),
                        "plateCode" to plateCode.trim().take(40),
                        "inspectionDate" to inspectionDate.trim().take(30),
                        "insuranceDate" to insuranceDate.trim().take(30),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                return existingItem.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        } else {
            val docId = newDocId()
            val docRef = db.collection("users").document(uid)
                .collection("equipment").document(docId)
            val payload = mapOf(
                "userId" to uid,
                "name" to name.trim().take(120),
                "model" to model.trim().take(100),
                "plateCode" to plateCode.trim().take(40),
                "inspectionDate" to inspectionDate.trim().take(30),
                "insuranceDate" to insuranceDate.trim().take(30),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            try {
                docRef.set(payload).await()
                return docId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        }
    }

    suspend fun deleteEquipment(id: String) = deleteSubDoc("equipment", id)

    // Finance
    fun observeFinance(): Flow<List<FinanceEntry>> =
        observeUserSubcollection("finance", FinanceEntry::class.java)

    suspend fun addFinance(
        type: String,
        date: String,
        fieldName: String,
        amount: Double,
        category: String,
    ): String {
        val uid = requireUserId()
        val docId = newDocId()
        val docRef = db.collection("users").document(uid).collection("finance").document(docId)
        val safeType = if (type == "income") "income" else "expense"
        val payload = mapOf(
            "userId" to uid,
            "type" to safeType,
            "date" to date.trim().take(30),
            "fieldName" to fieldName.trim().take(120),
            "amount" to amount,
            "category" to category.trim().take(120),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFinance(id: String) = deleteSubDoc("finance", id)

    // Harvest
    fun observeHarvests(): Flow<List<HarvestEntry>> =
        observeUserSubcollection("harvests", HarvestEntry::class.java)

    suspend fun addHarvest(
        date: String,
        crop: String,
        quantityKg: Double,
        pricePerKg: Double,
    ): String {
        val uid = requireUserId()
        val docId = newDocId()
        val docRef = db.collection("users").document(uid).collection("harvests").document(docId)
        val payload = mapOf(
            "userId" to uid,
            "date" to date.trim().take(30),
            "crop" to crop.trim().take(140),
            "quantityKg" to quantityKg,
            "pricePerKg" to pricePerKg,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteHarvest(id: String) = deleteSubDoc("harvests", id)

    // Subsidy Declarations
    fun observeDeclarations(): Flow<List<SubsidyDeclaration>> =
        observeUserSubcollection("declarations", SubsidyDeclaration::class.java)

    suspend fun addDeclaration(date: String, title: String): String {
        val uid = requireUserId()
        val docId = newDocId()
        val docRef = db.collection("users").document(uid).collection("declarations").document(docId)
        val payload = mapOf(
            "userId" to uid,
            "date" to date.trim().take(30),
            "title" to title.trim().take(160),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDeclaration(id: String) = deleteSubDoc("declarations", id)

    // Custom Pesticides
    fun observeCustomPesticides(): Flow<List<CustomPesticide>> =
        observeUserSubcollection("customPesticides", CustomPesticide::class.java)

    suspend fun addCustomPesticide(
        name: String,
        maxRate: Double,
        unit: String,
        quarantineDays: Int,
        safetyClass: String,
        target: String,
    ): String {
        val uid = requireUserId()
        val docId = newDocId()
        val docRef = db.collection("users").document(uid).collection("customPesticides").document(docId)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(140),
            "maxRate" to maxRate,
            "unit" to unit.trim().take(20),
            "quarantineDays" to quarantineDays.toLong(),
            "safetyClass" to safetyClass.trim().take(40),
            "target" to target.trim().take(200),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCustomPesticide(id: String) = deleteSubDoc("customPesticides", id)

    // Custom Links
    fun observeCustomLinks(): Flow<List<CustomLink>> =
        observeUserSubcollection("customLinks", CustomLink::class.java)

    suspend fun addCustomLink(name: String, url: String): String {
        val uid = requireUserId()
        val docId = newDocId()
        val docRef = db.collection("users").document(uid).collection("customLinks").document(docId)
        val normalizedUrl = if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            url.trim()
        } else {
            "https://${url.trim()}"
        }
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(140),
            "url" to normalizedUrl.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCustomLink(id: String) = deleteSubDoc("customLinks", id)

    // Admin Global Entries (Read: signed-in users; Write: verified admin email only)
    fun observeAdminEntries(): Flow<List<AdminContentItem>> = flow {
        requireUserId()
        emitAll(
            db.collection("adminEntries")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AdminContentItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "adminEntries")
                    }
                    emit(emptyList())
                }
        )
    }

    suspend fun addAdminEntry(
        category: String,
        region: String,
        title: String,
        details: String,
        contactOrUrl: String,
    ): String {
        val uid = requireUserId()
        val email = auth.currentUser?.email
        require(isAdminEmail(email)) { "Admin access restricted to $ADMIN_EMAIL" }
        val docId = newDocId()
        val docRef = db.collection("adminEntries").document(docId)
        val safeCategory = if (category in setOf("notice", "seed", "station", "calendar")) category else "notice"
        val payload = mapOf(
            "userId" to uid,
            "createdByEmail" to ADMIN_EMAIL,
            "category" to safeCategory,
            "region" to region.trim().take(140),
            "title" to title.trim().take(160),
            "details" to details.trim().take(2000),
            "contactOrUrl" to contactOrUrl.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAdminEntry(id: String) {
        requireUserId()
        require(isAdminEmail(auth.currentUser?.email)) { "Admin access restricted to $ADMIN_EMAIL" }
        require(id.isNotBlank()) { "Document ID must not be blank" }
        val docRef = db.collection("adminEntries").document(id)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private suspend fun deleteSubDoc(collectionName: String, docId: String) {
        val uid = requireUserId()
        require(docId.isNotBlank()) { "Document ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection(collectionName).document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
