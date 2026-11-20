package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.EquipmentItem
import com.example.data.model.FinanceEntry
import com.example.domain.BackupManager
import com.example.i18n.AppLanguage
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import com.example.ui.viewmodel.EquipmentAlert
import com.example.ui.viewmodel.EquipmentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class LocalGalleryPhoto(
    val fileName: String,
    val file: File,
    val capturedAtMillis: Long,
    val caption: String,
)

fun daysUntilIsoDate(dateStr: String): Long? {
    if (dateStr.isBlank()) return null
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val targetDate = fmt.parse(dateStr.trim()) ?: return null
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCal = Calendar.getInstance().apply {
            time = targetDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        TimeUnit.MILLISECONDS.toDays(targetCal.timeInMillis - todayCal.timeInMillis)
    } catch (_: Exception) {
        null
    }
}

fun evaluateEquipmentStatus(dateStr: String, warningDays: Int = 30): EquipmentStatus? {
    val daysLeft = daysUntilIsoDate(dateStr) ?: return null
    val lead = warningDays.coerceAtLeast(1)
    return when {
        daysLeft < 0 -> EquipmentStatus.OVERDUE
        daysLeft <= lead -> EquipmentStatus.SOON
        else -> EquipmentStatus.VALID
    }
}

fun collectEquipmentAlerts(items: List<EquipmentItem>, warningDays: Int = 30): List<EquipmentAlert> {
    val alerts = mutableListOf<EquipmentAlert>()
    items.forEach { item ->
        val insp = evaluateEquipmentStatus(item.inspectionDate, warningDays)
        val ins = evaluateEquipmentStatus(item.insuranceDate, warningDays)
        if (insp == EquipmentStatus.OVERDUE || ins == EquipmentStatus.OVERDUE) {
            alerts.add(EquipmentAlert(item.name, EquipmentStatus.OVERDUE))
        } else if (insp == EquipmentStatus.SOON || ins == EquipmentStatus.SOON) {
            alerts.add(EquipmentAlert(item.name, EquipmentStatus.SOON))
        }
    }
    return alerts
}

class LocalRecordsStore(context: Context) {

    private val appContext = context.applicationContext
    private val equipmentFile = File(appContext.filesDir, "local_equipment.json")
    private val financeFile = File(appContext.filesDir, "local_finance.json")
    private val photosDir = File(appContext.filesDir, "field_photos")
    private val captionsFile = File(photosDir, "captions.json")

    private val _equipment = MutableStateFlow(loadEquipment())
    val equipment: StateFlow<List<EquipmentItem>> = _equipment.asStateFlow()

    private val _finance = MutableStateFlow(loadFinance())
    val finance: StateFlow<List<FinanceEntry>> = _finance.asStateFlow()

    private val _photos = MutableStateFlow<List<LocalGalleryPhoto>>(emptyList())
    val photos: StateFlow<List<LocalGalleryPhoto>> = _photos.asStateFlow()

    init {
        refreshPhotos()
    }

    // --- Equipment CRUD ---
    fun saveEquipment(
        existing: EquipmentItem?,
        name: String,
        model: String,
        plateCode: String,
        inspectionDate: String,
        insuranceDate: String,
    ) {
        val current = _equipment.value.toMutableList()
        val updatedItem = EquipmentItem(
            id = existing?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            userId = "local",
            name = name.trim(),
            model = model.trim(),
            plateCode = plateCode.trim(),
            inspectionDate = inspectionDate.trim(),
            insuranceDate = insuranceDate.trim(),
        )
        if (existing != null) {
            val idx = current.indexOfFirst { it.id == existing.id }
            if (idx >= 0) {
                current[idx] = updatedItem
            } else {
                current.add(0, updatedItem)
            }
        } else {
            current.add(0, updatedItem)
        }
        persistEquipment(current)
        _equipment.value = current
    }

    fun deleteEquipment(id: String) {
        val updated = _equipment.value.filterNot { it.id == id }
        persistEquipment(updated)
        _equipment.value = updated
    }

    private fun loadEquipment(): List<EquipmentItem> {
        if (!equipmentFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(equipmentFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name")
                    if (name.isBlank()) continue
                    add(
                        EquipmentItem(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            userId = "local",
                            name = name,
                            model = o.optString("model"),
                            plateCode = o.optString("plateCode"),
                            inspectionDate = o.optString("inspectionDate"),
                            insuranceDate = o.optString("insuranceDate"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistEquipment(list: List<EquipmentItem>) {
        val arr = JSONArray().apply {
            list.forEach { e ->
                put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("name", e.name)
                        put("model", e.model)
                        put("plateCode", e.plateCode)
                        put("inspectionDate", e.inspectionDate)
                        put("insuranceDate", e.insuranceDate)
                    }
                )
            }
        }
        equipmentFile.writeText(arr.toString())
    }

    // --- Finance / Simple Accounting CRUD ---
    fun addFinance(
        type: String,
        date: String,
        fieldName: String,
        amount: Double,
        category: String,
    ) {
        val normalizedType = if (type.equals("income", ignoreCase = true)) "income" else "expense"
        val entry = FinanceEntry(
            id = UUID.randomUUID().toString(),
            userId = "local",
            type = normalizedType,
            date = date.trim(),
            fieldName = fieldName.trim(),
            amount = amount,
            category = category.trim(),
        )
        val updated = listOf(entry) + _finance.value
        persistFinance(updated)
        _finance.value = updated
    }

    fun deleteFinance(id: String) {
        val updated = _finance.value.filterNot { it.id == id }
        persistFinance(updated)
        _finance.value = updated
    }

    private fun loadFinance(): List<FinanceEntry> {
        if (!financeFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(financeFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val amount = o.optDouble("amount", 0.0)
                    if (amount <= 0.0) continue
                    add(
                        FinanceEntry(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            userId = "local",
                            type = o.optString("type", "expense"),
                            date = o.optString("date"),
                            fieldName = o.optString("fieldName"),
                            amount = amount,
                            category = o.optString("category"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistFinance(list: List<FinanceEntry>) {
        val arr = JSONArray().apply {
            list.forEach { x ->
                put(
                    JSONObject().apply {
                        put("id", x.id)
                        put("type", x.type)
                        put("date", x.date)
                        put("fieldName", x.fieldName)
                        put("amount", x.amount)
                        put("category", x.category)
                    }
                )
            }
        }
        financeFile.writeText(arr.toString())
    }

    fun exportBackupJson(): String = BackupManager.exportToJson(
        fields = emptyList(),
        equipment = _equipment.value,
        finance = _finance.value,
        harvests = emptyList(),
        declarations = emptyList(),
        customPesticides = emptyList(),
        customLinks = emptyList(),
    )

    fun importBackupJson(jsonText: String): Int {
        val root = JSONObject(jsonText)
        var importedCount = 0

        root.optJSONArray("equipment")?.let { arr ->
            val current = _equipment.value.toMutableList()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isNotBlank()) {
                    current.add(
                        0,
                        EquipmentItem(
                            id = UUID.randomUUID().toString(),
                            userId = "local",
                            name = name,
                            model = o.optString("model").trim(),
                            plateCode = o.optString("plateCode").trim(),
                            inspectionDate = o.optString("inspectionDate").trim(),
                            insuranceDate = o.optString("insuranceDate").trim(),
                        )
                    )
                    importedCount++
                }
            }
            persistEquipment(current)
            _equipment.value = current
        }

        root.optJSONArray("finance")?.let { arr ->
            val current = _finance.value.toMutableList()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val amount = o.optDouble("amount", 0.0)
                if (amount > 0.0) {
                    val rawType = o.optString("type", "expense")
                    current.add(
                        0,
                        FinanceEntry(
                            id = UUID.randomUUID().toString(),
                            userId = "local",
                            type = if (rawType.equals("income", ignoreCase = true)) "income" else "expense",
                            date = o.optString("date").trim(),
                            fieldName = o.optString("fieldName").trim(),
                            amount = amount,
                            category = o.optString("category").trim(),
                        )
                    )
                    importedCount++
                }
            }
            persistFinance(current)
            _finance.value = current
        }

        return importedCount
    }

    // --- Offline Camera & Photo Gallery ---
    fun generateDefaultPhotoTitle(
        language: AppLanguage = AppLanguage.AZ,
        isScan: Boolean = false,
        cropHint: String? = null,
    ): String {
        val strings = when (language) {
            AppLanguage.AZ -> StringsAz
            AppLanguage.EN -> StringsEn
            AppLanguage.RU -> StringsRu
        }
        val nextIndex = (_photos.value.size + 1).coerceAtLeast(1)
        val timeStamp = SimpleDateFormat("dd.MM HH:mm", language.locale).format(System.currentTimeMillis())
        val base = when {
            !cropHint.isNullOrBlank() -> "${strings.photoDefaultScanPrefix}: ${cropHint.trim()}"
            isScan -> strings.photoDefaultScanPrefix
            else -> strings.photoDefaultFieldPrefix
        }
        return "$base #$nextIndex ($timeStamp)"
    }

    fun createCameraPhotoTarget(): Pair<Uri, File> {
        ensurePhotosDir()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val shortId = UUID.randomUUID().toString().take(6)
        val file = File(photosDir, "aqro_${stamp}_$shortId.jpg")
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return uri to file
    }

    fun getShareUri(file: File): Uri =
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)

    fun finalizeCapturedPhoto(
        file: File,
        caption: String = "",
        language: AppLanguage = AppLanguage.AZ,
        isScan: Boolean = false,
        cropHint: String? = null,
    ): Boolean {
        if (!file.exists() || file.length() <= 0L) {
            runCatching { if (file.exists()) file.delete() }
            return false
        }
        val resolvedTitle = caption.trim().ifBlank {
            generateDefaultPhotoTitle(language = language, isScan = isScan, cropHint = cropHint)
        }
        setCaptionInternal(file.name, resolvedTitle)
        exportFileToSystemGallery(file, resolvedTitle)
        refreshPhotos()
        return true
    }

    fun saveScannerFileToGallery(
        tempFile: File,
        cropHint: String? = null,
        language: AppLanguage = AppLanguage.AZ,
    ): LocalGalleryPhoto? {
        if (!tempFile.exists() || tempFile.length() <= 0L) return null
        ensurePhotosDir()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val shortId = UUID.randomUUID().toString().take(6)
        val destFile = File(photosDir, "aqro_scan_${stamp}_$shortId.jpg")
        val copied = runCatching {
            tempFile.copyTo(destFile, overwrite = true)
            destFile.exists() && destFile.length() > 0L
        }.getOrDefault(false)
        if (!copied) {
            runCatching { if (destFile.exists()) destFile.delete() }
            return null
        }
        val title = generateDefaultPhotoTitle(language = language, isScan = true, cropHint = cropHint)
        setCaptionInternal(destFile.name, title)
        exportFileToSystemGallery(destFile, title)
        refreshPhotos()
        return _photos.value.firstOrNull { it.fileName == destFile.name }
    }

    fun importPickedPhoto(
        sourceUri: Uri,
        caption: String = "",
        language: AppLanguage = AppLanguage.AZ,
    ): LocalGalleryPhoto? {
        ensurePhotosDir()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val shortId = UUID.randomUUID().toString().take(6)
        val destFile = File(photosDir, "aqro_${stamp}_$shortId.jpg")
        val copied = runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
        }.getOrDefault(false)

        if (!copied || !destFile.exists() || destFile.length() <= 0L) {
            runCatching { if (destFile.exists()) destFile.delete() }
            return null
        }

        val resolvedTitle = caption.trim().ifBlank {
            generateDefaultPhotoTitle(language = language, isScan = false)
        }
        setCaptionInternal(destFile.name, resolvedTitle)
        refreshPhotos()
        return _photos.value.firstOrNull { it.fileName == destFile.name }
    }

    fun renamePhoto(fileName: String, newName: String): Boolean {
        val cleaned = newName.trim()
        if (cleaned.isBlank()) return false
        setCaptionInternal(fileName, cleaned)
        refreshPhotos()
        return true
    }

    fun updatePhotoCaption(fileName: String, caption: String) {
        setCaptionInternal(fileName, caption.trim())
        refreshPhotos()
    }

    private fun exportFileToSystemGallery(sourceFile: File, displayTitle: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val safeName = displayTitle
                .replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "_")
                .trim()
                .ifBlank { "aqro_${System.currentTimeMillis()}" }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$safeName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AqroMeslehet")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(insertedUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(insertedUri, values, null, null)
        }
    }

    fun deletePhoto(fileName: String) {
        val target = File(photosDir, fileName)
        runCatching {
            if (target.exists()) target.delete()
        }
        val captions = loadCaptions().toMutableMap()
        if (captions.remove(fileName) != null) {
            persistCaptions(captions)
        }
        refreshPhotos()
    }

    fun refreshPhotos() {
        if (!photosDir.exists()) {
            _photos.value = emptyList()
            return
        }
        val captions = loadCaptions()
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
        val list = photosDir.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    file.length() > 0L &&
                    file.extension.lowercase(Locale.US) in imageExtensions
            }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                LocalGalleryPhoto(
                    fileName = file.name,
                    file = file,
                    capturedAtMillis = file.lastModified(),
                    caption = captions[file.name].orEmpty(),
                )
            }
            ?.toList()
            ?: emptyList()
        _photos.value = list
    }

    private fun ensurePhotosDir() {
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
    }

    private fun loadCaptions(): Map<String, String> {
        if (!captionsFile.exists()) return emptyMap()
        return runCatching {
            val obj = JSONObject(captionsFile.readText())
            buildMap {
                obj.keys().forEach { key ->
                    val v = obj.optString(key).trim()
                    if (v.isNotEmpty()) put(key, v)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun persistCaptions(map: Map<String, String>) {
        ensurePhotosDir()
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        captionsFile.writeText(obj.toString())
    }

    private fun setCaptionInternal(fileName: String, caption: String) {
        val map = loadCaptions().toMutableMap()
        if (caption.isBlank()) {
            map.remove(fileName)
        } else {
            map[fileName] = caption
        }
        persistCaptions(map)
    }
}
