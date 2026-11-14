package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addField_authenticatedOwner_createsAndObservesField() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FarmRepository(firestore, auth)

        val fieldId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addField(
                name = "North Plot",
                crop = "Wheat",
                sizeHa = 3.25,
                widthM = 125.0,
                lengthM = 260.0,
                location = "Barda",
                coords = listOf("40.678200,46.360600", "40.679500,46.360600", "40.679500,46.362500"),
            )
        }
        assertTrue(fieldId.isNotBlank())

        val fields = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeFields().first { list -> list.any { it.id == fieldId } }
        }
        assertTrue(fields.any { it.id == fieldId && it.name == "North Plot" })
    }

    @Test
    fun saveProfile_andEquipment_andFinance_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FarmRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveProfile("Ali Mammadov", "+994501112233", "Aghjabadi")
            repo.saveEquipment(null, "Tractor", "MTZ-82", "90-AA-100", "2027-01-15", "2027-02-10")
            repo.addFinance("income", "2026-11-14", "North Plot", 1250.0, "Harvest Sales")
        }

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProfile().first { it != null }
        }
        assertEquals("Ali Mammadov", profile?.fullName)

        val equipment = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeEquipment().first { it.isNotEmpty() }
        }
        assertTrue(equipment.any { it.name == "Tractor" })
    }

    @Test
    fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FarmRepository(firestore, auth)
        val eqId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveEquipment(null, "Harvester", "Class", "10-BB-200", "2026-12-01", "2026-12-01")
        }

        signInTestUser(BOB_EMAIL)
        var denied = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("equipment").document(eqId)
                    .get().await()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException() ?: (e as? FirebaseFirestoreException)
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Cross-user read must be denied with PERMISSION_DENIED", denied)
    }

    @Test
    fun unauthenticatedAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()

        var denied = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("fields").get().await()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException() ?: (e as? FirebaseFirestoreException)
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Unauthenticated read must be denied with PERMISSION_DENIED", denied)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_agro@test.com"
        const val BOB_EMAIL = "bob_agro@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
