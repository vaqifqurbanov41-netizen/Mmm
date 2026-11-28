package com.aistudio.testbaza.core.data.remote

import android.util.Log
import com.aistudio.testbaza.core.domain.ExamEvaluationSummary
import com.aistudio.testbaza.core.domain.QuizQuestion
import com.aistudio.testbaza.core.domain.SharedExam
import com.aistudio.testbaza.core.domain.SharedExamAttempt
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

enum class TbOperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
}

fun handleTestBazaFirestoreError(
    exception: Throwable,
    operationType: TbOperationType,
    path: String?,
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

class SharedExamRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    private val secureRandom = SecureRandom()

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        require(!uid.isNullOrBlank()) {
            "İmtahanı paylaşmaq və ya nəticələrə baxmaq üçün Google hesabı ilə daxil olun."
        }
        return uid
    }

    fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder("TB")
        repeat(6) {
            sb.append(chars[secureRandom.nextInt(chars.length)])
        }
        return sb.toString()
    }

    suspend fun publishSharedExam(
        title: String,
        sourceBankTitle: String,
        ownerName: String,
        questions: List<QuizQuestion>,
        durationMinutes: Int,
        shuffleOptions: Boolean,
    ): Result<SharedExam> {
        val examCode = generateShareCode()
        val path = "sharedExams/$examCode"
        return try {
            val uid = requireUserId()
            require(questions.isNotEmpty()) { "Paylaşmaq üçün ən azı 1 sual olmalıdır." }
            val bounded = questions.take(150)
            val safeTitle = title.trim().ifBlank { "Testbaza İmtahanı" }.take(160)
            val safeSourceTitle = sourceBankTitle.trim().take(160)
            val safeOwnerName = ownerName.trim().ifBlank {
                auth.currentUser?.displayName?.trim().orEmpty().ifBlank { "Müəllim / Paylaşan" }
            }.take(80)
            val clampedMinutes = durationMinutes.coerceIn(1, 180)

            val questionMaps = bounded.mapIndexed { idx, q ->
                mapOf(
                    "qNum" to (q.questionNumber.takeIf { it > 0 } ?: (idx + 1)).toLong(),
                    "text" to q.questionText.take(1200),
                    "options" to q.allOptions.take(8).map { it.take(500) },
                    "correct" to q.correctText.ifBlank { q.allOptions.firstOrNull().orEmpty() }.take(500),
                )
            }

            val payload = mapOf(
                "ownerId" to uid,
                "ownerName" to safeOwnerName,
                "title" to safeTitle,
                "sourceBankTitle" to safeSourceTitle,
                "questionCount" to questionMaps.size.toLong(),
                "durationMinutes" to clampedMinutes.toLong(),
                "shuffleOptions" to shuffleOptions,
                "questions" to questionMaps,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            db.collection("sharedExams").document(examCode).set(payload).await()
            val saved = db.collection("sharedExams").document(examCode)
                .get()
                .await()
                .toObject(SharedExam::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: error("Paylaşılan imtahan yaradıldı, lakin oxuna bilmədi.")
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleTestBazaFirestoreError(e, TbOperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchSharedExamByCode(rawCode: String): Result<SharedExam> {
        val code = rawCode.trim().uppercase()
        val path = "sharedExams/$code"
        return try {
            requireUserId()
            require(code.matches(Regex("^[a-zA-Z0-9_\\-]{4,128}$"))) {
                "İmtahan kodu düzgün deyil (məsələn: TB4K9M2A)."
            }
            val snap = db.collection("sharedExams").document(code).get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("'$code' kodlu imtahan tapılmadı."))
            }
            val exam = snap.toObject(SharedExam::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("İmtahan məlumatları oxuna bilmədi."))
            Result.success(exam)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleTestBazaFirestoreError(e, TbOperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observeMySharedExams(ownerId: String): Flow<List<SharedExam>> {
        require(ownerId.isNotBlank()) { "ownerId boş ola bilməz" }
        return db.collection("sharedExams")
            .whereEqualTo("ownerId", ownerId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SharedExam::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    fun observeConsentedAttempts(examId: String, ownerId: String): Flow<List<SharedExamAttempt>> {
        require(examId.isNotBlank() && ownerId.isNotBlank()) { "examId və ownerId tələb olunur" }
        return db.collection("sharedExams")
            .document(examId)
            .collection("attempts")
            .whereEqualTo("examOwnerId", ownerId)
            .whereEqualTo("shareConsentGranted", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SharedExamAttempt::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    suspend fun deleteSharedExam(examId: String): Result<Unit> {
        val path = "sharedExams/$examId"
        return try {
            requireUserId()
            require(examId.isNotBlank()) { "İmtahan kodu boşdur." }
            db.collection("sharedExams").document(examId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleTestBazaFirestoreError(e, TbOperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun submitConsentedAttempt(
        exam: SharedExam,
        participantName: String,
        summary: ExamEvaluationSummary,
        totalElapsedSeconds: Long,
        durationLimitSeconds: Long,
        timedOut: Boolean,
        consentGranted: Boolean,
    ): Result<String> {
        if (!consentGranted) {
            return Result.failure(IllegalStateException("İştirakçı icazəsi olmadan nəticə paylaşıla bilməz."))
        }
        val docRef = db.collection("sharedExams")
            .document(exam.id)
            .collection("attempts")
            .document()
        val path = docRef.path
        return try {
            val uid = requireUserId()
            val safeParticipantName = participantName.trim().ifBlank {
                auth.currentUser?.displayName?.trim().orEmpty().ifBlank { "İştirakçı" }
            }.take(80)

            val wrongDetailMaps = summary.wrongDetails.map { item ->
                mapOf(
                    "questionNumber" to item.questionNumber,
                    "questionText" to item.questionText.take(240),
                    "selectedAnswer" to item.selectedAnswer.take(240),
                    "correctAnswer" to item.correctAnswer.take(240),
                    "timeSpentSeconds" to item.timeSpentSeconds.coerceAtLeast(0L),
                )
            }
            val timingMaps = summary.questionTimings.map { item ->
                mapOf(
                    "questionNumber" to item.questionNumber,
                    "questionText" to item.questionText.take(240),
                    "status" to item.status,
                    "timeSpentSeconds" to item.timeSpentSeconds.coerceAtLeast(0L),
                )
            }

            val payload = mapOf(
                "examId" to exam.id,
                "examOwnerId" to exam.ownerId,
                "participantId" to uid,
                "participantName" to safeParticipantName,
                "shareConsentGranted" to true,
                "totalQuestions" to summary.totalQuestions.toLong(),
                "answeredCount" to summary.answeredCount.toLong(),
                "correctCount" to summary.correctCount.toLong(),
                "wrongCount" to summary.wrongCount.toLong(),
                "unansweredCount" to summary.unansweredCount.toLong(),
                "totalElapsedSeconds" to totalElapsedSeconds.coerceIn(0L, 86400L),
                "durationLimitSeconds" to durationLimitSeconds.coerceIn(60L, 10800L),
                "timedOut" to timedOut,
                "wrongDetails" to wrongDetailMaps,
                "questionTimings" to timingMaps,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleTestBazaFirestoreError(e, TbOperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAttempt(examId: String, attemptId: String): Result<Unit> {
        val path = "sharedExams/$examId/attempts/$attemptId"
        return try {
            requireUserId()
            require(examId.isNotBlank() && attemptId.isNotBlank()) { "Yanlış cəhd identifikatoru." }
            db.collection("sharedExams")
                .document(examId)
                .collection("attempts")
                .document(attemptId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleTestBazaFirestoreError(e, TbOperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
