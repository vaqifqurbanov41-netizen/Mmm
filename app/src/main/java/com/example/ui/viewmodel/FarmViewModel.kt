package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.ai.ChatHistoryTurn
import com.example.data.ai.OfflineAgronomistAdvisor
import com.example.data.firebase.isAppCheckTokenError
import com.example.data.local.PreferencesStore
import com.example.data.model.AdminContentItem
import com.example.data.model.CustomLink
import com.example.data.model.CustomPesticide
import com.example.data.model.EquipmentItem
import com.example.data.model.FarmField
import com.example.data.model.FarmerProfile
import com.example.data.model.FinanceEntry
import com.example.data.model.HarvestEntry
import com.example.data.model.SubsidyDeclaration
import com.example.data.remote.OpenMeteoResponse
import com.example.data.remote.OpenMeteoService
import com.example.data.repository.FarmRepository
import com.example.domain.BackupManager
import com.example.domain.ClimateHistorySummary
import com.example.domain.ClimateHistoryUtils
import com.example.domain.FieldMeasurements
import com.example.domain.GeoUtils
import com.example.domain.LatLon
import com.example.i18n.AppLanguage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WeatherUiState(
    val isLoading: Boolean = false,
    val response: OpenMeteoResponse? = null,
    val error: String? = null,
    val lat: Double = 40.6782,
    val lon: Double = 46.3606,
) {
    val hasFrostRisk: Boolean
        get() = response?.daily?.tempMin?.take(3)?.any { it <= 2.0 } == true

    val hasHighWind: Boolean
        get() {
            val currentWind = response?.current?.windSpeed10m ?: 0.0
            val maxDaily = response?.daily?.windMax?.take(2)?.maxOrNull() ?: 0.0
            return currentWind > 35.0 || maxDaily > 38.0
        }

    val hasHeavyRain: Boolean
        get() = (response?.daily?.precipitationSum?.take(3)?.sum() ?: 0.0) > 15.0

    val hasAnyHazard: Boolean
        get() = hasFrostRisk || hasHighWind || hasHeavyRain
}

data class ClimateHistoryUiState(
    val isLoading: Boolean = false,
    val locationLabel: String = "Gəncə-Daşkəsən",
    val lat: Double = 40.6782,
    val lon: Double = 46.3606,
    val startDate: String = ClimateHistoryUtils.defaultDateRange(24).first,
    val endDate: String = ClimateHistoryUtils.defaultDateRange(24).second,
    val elevationM: Double? = null,
    val summary: ClimateHistorySummary? = null,
    val error: String? = null,
)

data class ChatMessageUi(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false,
)

data class DocScanUiState(
    val isScanning: Boolean = false,
    val summary: String? = null,
    val detectedAreaHa: Double? = null,
    val error: String? = null,
)

enum class EquipmentStatus { OVERDUE, SOON, VALID }

data class EquipmentAlert(
    val itemName: String,
    val status: EquipmentStatus,
)

class FarmViewModel(
    private val repository: FarmRepository,
    private val preferencesStore: PreferencesStore,
    private val weatherService: OpenMeteoService,
    private val aiRepository: AiRepository,
    val currentUserId: String,
) : ViewModel() {

    private val _firestoreError = MutableStateFlow<String?>(null)
    val firestoreError: StateFlow<String?> = _firestoreError.asStateFlow()

    private fun <T> Flow<T>.safeStateIn(initial: T): StateFlow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Firestore stream error for user $currentUserId", rootError)
            _firestoreError.value = rootError.localizedMessage ?: rootError.message
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = initial,
        )

    val profile: StateFlow<FarmerProfile?> = repository.observeProfile().safeStateIn(null)

    val fields: StateFlow<List<FarmField>> = repository.observeFields()
        .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }
        .safeStateIn(emptyList())

    val equipment: StateFlow<List<EquipmentItem>> = repository.observeEquipment()
        .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }
        .safeStateIn(emptyList())

    val finance: StateFlow<List<FinanceEntry>> = repository.observeFinance()
        .map { list -> list.sortedByDescending { it.date } }
        .safeStateIn(emptyList())

    val harvests: StateFlow<List<HarvestEntry>> = repository.observeHarvests()
        .map { list -> list.sortedByDescending { it.date } }
        .safeStateIn(emptyList())

    val declarations: StateFlow<List<SubsidyDeclaration>> = repository.observeDeclarations()
        .map { list -> list.sortedBy { it.date } }
        .safeStateIn(emptyList())

    val customPesticides: StateFlow<List<CustomPesticide>> = repository.observeCustomPesticides()
        .map { list -> list.sortedBy { it.name } }
        .safeStateIn(emptyList())

    val customLinks: StateFlow<List<CustomLink>> = repository.observeCustomLinks()
        .map { list -> list.sortedBy { it.name } }
        .safeStateIn(emptyList())

    val adminEntries: StateFlow<List<AdminContentItem>> = repository.observeAdminEntries()
        .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }
        .safeStateIn(emptyList())

    val equipmentReminderDays: StateFlow<Int> = preferencesStore.equipmentReminderDaysFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 7,
        )

    // Weather state
    private val _weatherState = MutableStateFlow(WeatherUiState())
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    // Historical Climate state
    private val _climateHistoryState = MutableStateFlow(ClimateHistoryUiState())
    val climateHistoryState: StateFlow<ClimateHistoryUiState> = _climateHistoryState.asStateFlow()

    // Map & Field Polygon State
    private val _mapPoints = MutableStateFlow<List<LatLon>>(emptyList())
    val mapPoints: StateFlow<List<LatLon>> = _mapPoints.asStateFlow()

    val measurements: StateFlow<FieldMeasurements> = _mapPoints
        .map { pts -> GeoUtils.calculateMeasurements(pts) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = FieldMeasurements(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        )

    private val _mapCenter = MutableStateFlow(LatLon(40.6782, 46.3606))
    val mapCenter: StateFlow<LatLon> = _mapCenter.asStateFlow()

    private val _mapZoom = MutableStateFlow(15)
    val mapZoom: StateFlow<Int> = _mapZoom.asStateFlow()

    private val _isSatellite = MutableStateFlow(true)
    val isSatellite: StateFlow<Boolean> = _isSatellite.asStateFlow()

    private val _gpsLocation = MutableStateFlow<LatLon?>(null)
    val gpsLocation: StateFlow<LatLon?> = _gpsLocation.asStateFlow()

    private val _gpsAccuracyM = MutableStateFlow<Float?>(null)
    val gpsAccuracyM: StateFlow<Float?> = _gpsAccuracyM.asStateFlow()

    private val _docScanState = MutableStateFlow(DocScanUiState())
    val docScanState: StateFlow<DocScanUiState> = _docScanState.asStateFlow()

    // AI Chat State
    private val _chatMessages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageUi>> = _chatMessages.asStateFlow()

    private val _isChatStreaming = MutableStateFlow(false)
    val isChatStreaming: StateFlow<Boolean> = _isChatStreaming.asStateFlow()
    private var chatJob: Job? = null

    // AI Plant Scanner State
    private val _scannerBitmap = MutableStateFlow<Bitmap?>(null)
    val scannerBitmap: StateFlow<Bitmap?> = _scannerBitmap.asStateFlow()

    private val _scannerResult = MutableStateFlow("")
    val scannerResult: StateFlow<String> = _scannerResult.asStateFlow()

    private val _isScannerLoading = MutableStateFlow(false)
    val isScannerLoading: StateFlow<Boolean> = _isScannerLoading.asStateFlow()

    private val _scannerError = MutableStateFlow<String?>(null)
    val scannerError: StateFlow<String?> = _scannerError.asStateFlow()
    private var scannerJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesStore.coordinatesFlow.collect { (lat, lon) ->
                if (_weatherState.value.response == null && !_weatherState.value.isLoading) {
                    _mapCenter.value = LatLon(lat, lon)
                    loadWeather(lat, lon)
                }
            }
        }
    }

    fun clearFirestoreError() {
        _firestoreError.value = null
    }

    fun loadWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherState.value = _weatherState.value.copy(
                isLoading = true,
                error = null,
                lat = lat,
                lon = lon,
            )
            try {
                preferencesStore.setCoordinates(lat, lon)
                val res = weatherService.getForecast(latitude = lat, longitude = lon)
                _weatherState.value = WeatherUiState(
                    isLoading = false,
                    response = res,
                    error = null,
                    lat = lat,
                    lon = lon,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Weather fetch failed", e)
                _weatherState.value = _weatherState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Weather error",
                )
            }
        }
    }

    fun loadClimateHistory(
        lat: Double,
        lon: Double,
        startDate: String,
        endDate: String,
        locationLabel: String,
    ) {
        viewModelScope.launch {
            _climateHistoryState.value = _climateHistoryState.value.copy(
                isLoading = true,
                lat = lat,
                lon = lon,
                startDate = startDate,
                endDate = endDate,
                locationLabel = locationLabel,
                error = null,
            )
            try {
                val res = weatherService.getHistoricalClimate(
                    latitude = lat,
                    longitude = lon,
                    startDate = startDate,
                    endDate = endDate,
                )
                val summary = ClimateHistoryUtils.summarizeResponse(res)
                _climateHistoryState.value = ClimateHistoryUiState(
                    isLoading = false,
                    locationLabel = locationLabel,
                    lat = res.latitude,
                    lon = res.longitude,
                    startDate = startDate,
                    endDate = endDate,
                    elevationM = res.elevation,
                    summary = summary,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Historical climate fetch failed", e)
                _climateHistoryState.value = _climateHistoryState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Climate history error",
                )
            }
        }
    }

    // Map manipulation
    fun addMapPoint(point: LatLon) {
        _mapPoints.value = _mapPoints.value + point
    }

    fun updateMapPoint(index: Int, point: LatLon) {
        val current = _mapPoints.value.toMutableList()
        if (index in current.indices) {
            current[index] = point
            _mapPoints.value = current
        }
    }

    fun undoMapPoint() {
        val current = _mapPoints.value
        if (current.isNotEmpty()) {
            _mapPoints.value = current.dropLast(1)
        }
    }

    fun clearMapPoints() {
        _mapPoints.value = emptyList()
    }

    fun setMapPolygon(points: List<LatLon>) {
        _mapPoints.value = points
        if (points.isNotEmpty()) {
            val avgLat = points.map { it.lat }.average()
            val avgLon = points.map { it.lon }.average()
            _mapCenter.value = LatLon(avgLat, avgLon)
        }
    }

    fun setMapCenter(center: LatLon) {
        _mapCenter.value = center
    }

    fun setMapZoom(zoom: Int) {
        _mapZoom.value = zoom.coerceIn(5, 19)
    }

    fun toggleMapLayer() {
        _isSatellite.value = !_isSatellite.value
    }

    fun updateGpsFix(lat: Double, lon: Double, accuracyM: Float) {
        val loc = LatLon(lat, lon)
        _gpsLocation.value = loc
        _gpsAccuracyM.value = accuracyM
        _mapCenter.value = loc
        _mapZoom.value = maxOf(_mapZoom.value, 16)
        viewModelScope.launch {
            preferencesStore.setCoordinates(lat, lon)
        }
    }

    fun focusFieldOnMap(field: FarmField) {
        val decoded = field.coords.mapNotNull { GeoUtils.decodeCoord(it) }
        if (decoded.isNotEmpty()) {
            setMapPolygon(decoded)
            _mapZoom.value = 16
        }
    }

    fun scanFieldDocument(bitmap: Bitmap, language: AppLanguage, onResult: (Double?) -> Unit) {
        viewModelScope.launch {
            _docScanState.value = DocScanUiState(isScanning = true)
            try {
                val center = _mapCenter.value
                val res = aiRepository.scanFieldDocument(bitmap, center.lat, center.lon, language)
                if (res.coordinates.size >= 3) {
                    setMapPolygon(res.coordinates.map { (lat, lon) -> LatLon(lat, lon) })
                    _mapZoom.value = 16
                }
                _docScanState.value = DocScanUiState(
                    isScanning = false,
                    summary = res.summary,
                    detectedAreaHa = res.areaHa,
                    error = null,
                )
                onResult(res.areaHa)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Document scan error", e)
                _docScanState.value = DocScanUiState(
                    isScanning = false,
                    error = e.localizedMessage ?: "Document scan failed",
                )
            }
        }
    }

    // AI Chat
    fun sendAiQuestion(question: String, language: AppLanguage) {
        val trimmed = question.trim()
        if (trimmed.isBlank() || _isChatStreaming.value) return

        val historySnapshot = _chatMessages.value.map {
            ChatHistoryTurn(isUser = it.isUser, text = it.text)
        }
        val userMsg = ChatMessageUi(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = trimmed,
        )
        val botId = "b_${System.currentTimeMillis() + 1}"
        val placeholderBot = ChatMessageUi(
            id = botId,
            isUser = false,
            text = "",
            isStreaming = true,
        )
        _chatMessages.value = _chatMessages.value + userMsg + placeholderBot
        _isChatStreaming.value = true

        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            val currentProfile = profile.value
            try {
                aiRepository.askAgronomistStream(
                    question = trimmed,
                    history = historySnapshot,
                    farmerName = currentProfile?.fullName.orEmpty(),
                    farmerRegion = currentProfile?.place.orEmpty(),
                    language = language,
                ).collect { accumulated ->
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == botId) msg.copy(text = accumulated, isStreaming = true) else msg
                    }
                }
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == botId) msg.copy(isStreaming = false) else msg
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI chat error", e)
                val errText = e.localizedMessage ?: "AI error"
                val offlineAnswer = OfflineAgronomistAdvisor.answerOffline(
                    question = trimmed,
                    language = language,
                    aiFailureNote = errText,
                )
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == botId) {
                        msg.copy(
                            text = if (msg.text.isBlank()) offlineAnswer else "${msg.text}\n\n($errText)",
                            isStreaming = false,
                        )
                    } else msg
                }
            } finally {
                _isChatStreaming.value = false
            }
        }
    }

    fun clearChat() {
        chatJob?.cancel()
        _isChatStreaming.value = false
        _chatMessages.value = emptyList()
    }

    // Plant Scanner
    fun setScannerBitmap(bitmap: Bitmap?) {
        _scannerBitmap.value = bitmap
        _scannerError.value = null
    }

    fun analyzePlantPhoto(note: String, language: AppLanguage) {
        val bmp = _scannerBitmap.value
        val trimmedNote = note.trim()
        if (bmp == null && trimmedNote.isBlank()) return

        _isScannerLoading.value = true
        _scannerResult.value = ""
        _scannerError.value = null

        scannerJob?.cancel()
        scannerJob = viewModelScope.launch {
            try {
                aiRepository.analyzePlantPhotoStream(bmp, trimmedNote, language).collect { partial ->
                    _scannerResult.value = partial
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Plant scanner error", e)
                val errText = e.localizedMessage ?: "Analysis failed"
                val querySeed = trimmedNote.ifBlank {
                    when (language) {
                        AppLanguage.AZ -> "bitki xəstəlik yarpaq ləkə"
                        AppLanguage.EN -> "plant disease leaf spot"
                        AppLanguage.RU -> "болезнь растений пятна листьев"
                    }
                }
                _scannerResult.value = OfflineAgronomistAdvisor.answerOffline(
                    question = querySeed,
                    language = language,
                    aiFailureNote = errText,
                )
                if (isAppCheckTokenError(errText)) {
                    _scannerError.value = errText
                }
            } finally {
                _isScannerLoading.value = false
            }
        }
    }

    // Equipment alert evaluation
    fun setEquipmentReminderDays(days: Int) {
        viewModelScope.launch {
            preferencesStore.setEquipmentReminderDays(days)
        }
    }

    fun computeEquipmentStatus(dateStr: String, warningDays: Int = equipmentReminderDays.value): EquipmentStatus? =
        com.example.data.local.evaluateEquipmentStatus(dateStr, warningDays)

    fun getEquipmentAlerts(items: List<EquipmentItem>, warningDays: Int = equipmentReminderDays.value): List<EquipmentAlert> =
        com.example.data.local.collectEquipmentAlerts(items, warningDays)

    // Firestore CRUD Actions
    suspend fun saveProfile(fullName: String, phone: String, place: String) {
        repository.saveProfile(fullName, phone, place)
    }

    suspend fun addField(
        name: String,
        crop: String,
        sizeHa: Double,
        widthM: Double,
        lengthM: Double,
        location: String,
        coords: List<String>,
    ) {
        repository.addField(name, crop, sizeHa, widthM, lengthM, location, coords)
    }

    suspend fun deleteField(id: String) = repository.deleteField(id)

    suspend fun saveEquipment(
        existing: EquipmentItem?,
        name: String,
        model: String,
        plateCode: String,
        inspectionDate: String,
        insuranceDate: String,
    ) {
        repository.saveEquipment(existing, name, model, plateCode, inspectionDate, insuranceDate)
    }

    suspend fun deleteEquipment(id: String) = repository.deleteEquipment(id)

    suspend fun addFinance(
        type: String,
        date: String,
        fieldName: String,
        amount: Double,
        category: String,
    ) {
        repository.addFinance(type, date, fieldName, amount, category)
    }

    suspend fun deleteFinance(id: String) = repository.deleteFinance(id)

    suspend fun addHarvest(
        date: String,
        crop: String,
        quantityKg: Double,
        pricePerKg: Double,
    ) {
        repository.addHarvest(date, crop, quantityKg, pricePerKg)
    }

    suspend fun deleteHarvest(id: String) = repository.deleteHarvest(id)

    suspend fun addDeclaration(date: String, title: String) {
        repository.addDeclaration(date, title)
    }

    suspend fun deleteDeclaration(id: String) = repository.deleteDeclaration(id)

    suspend fun addCustomPesticide(
        name: String,
        maxRate: Double,
        unit: String,
        quarantineDays: Int,
        safetyClass: String,
        target: String,
    ) {
        repository.addCustomPesticide(name, maxRate, unit, quarantineDays, safetyClass, target)
    }

    suspend fun deleteCustomPesticide(id: String) = repository.deleteCustomPesticide(id)

    suspend fun addCustomLink(name: String, url: String) {
        repository.addCustomLink(name, url)
    }

    suspend fun deleteCustomLink(id: String) = repository.deleteCustomLink(id)

    suspend fun addAdminEntry(
        category: String,
        region: String,
        title: String,
        details: String,
        contactOrUrl: String,
    ) {
        repository.addAdminEntry(category, region, title, details, contactOrUrl)
    }

    suspend fun deleteAdminEntry(id: String) = repository.deleteAdminEntry(id)

    fun exportBackupJson(): String = BackupManager.exportToJson(
        fields = fields.value,
        equipment = equipment.value,
        finance = finance.value,
        harvests = harvests.value,
        declarations = declarations.value,
        customPesticides = customPesticides.value,
        customLinks = customLinks.value,
    )

    suspend fun importBackupJson(jsonText: String): Int =
        BackupManager.importFromJson(jsonText, repository)

    private companion object {
        const val TAG = "FarmViewModel"
    }
}
