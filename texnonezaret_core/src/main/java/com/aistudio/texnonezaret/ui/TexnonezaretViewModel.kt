package com.aistudio.texnonezaret.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.texnonezaret.data.PersistedSheetData
import com.aistudio.texnonezaret.data.SpreadsheetParser
import com.aistudio.texnonezaret.data.TexnonezaretStorage
import com.aistudio.texnonezaret.data.ThemeMode
import com.aistudio.texnonezaret.domain.ColumnField
import com.aistudio.texnonezaret.domain.ColumnMapping
import com.aistudio.texnonezaret.domain.InspectionStatusKind
import com.aistudio.texnonezaret.domain.SearchMode
import com.aistudio.texnonezaret.domain.SearchOutcome
import com.aistudio.texnonezaret.domain.StatusFilter
import com.aistudio.texnonezaret.domain.TexnonezaretEngine
import com.aistudio.texnonezaret.domain.VehicleRow
import com.aistudio.texnonezaret.security.SecurityGuard
import com.aistudio.texnonezaret.security.SecurityIntegrityReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PinFlowMode {
    SETUP,
    CONFIRM,
    ENTER,
    CHANGE_OLD,
}

data class PinUiState(
    val isLocked: Boolean = true,
    val mode: PinFlowMode = PinFlowMode.SETUP,
    val enteredDigits: Int = 0,
    val message: String? = null,
    val shakeTrigger: Int = 0,
    val cooldownSecondsLeft: Int = 0,
)

data class StatusSummaryCounts(
    val total: Int = 0,
    val valid: Int = 0,
    val soon: Int = 0,
    val overdue: Int = 0,
    val none: Int = 0,
)

data class SearchUiState(
    val isLoadingFile: Boolean = false,
    val fileError: String? = null,
    val statusBanner: String? = null,
    val sourceFileName: String = "",
    val raw: List<List<String>> = emptyList(),
    val hdr: Int = 0,
    val map: ColumnMapping = ColumnMapping(),
    val columnCount: Int = 0,
    val rows: List<VehicleRow> = emptyList(),
    val plateQuery: String = "",
    val nameQuery: String = "",
    val searchMode: SearchMode = SearchMode.PLATE,
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val summary: StatusSummaryCounts = StatusSummaryCounts(),
    val outcome: SearchOutcome = TexnonezaretEngine.searchRows(emptyList(), "", "", SearchMode.PLATE),
    val mapPanelExpanded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

sealed interface PendingInterventionAction {
    data object UnlockModeOnly : PendingInterventionAction
    data object ImportExcel : PendingInterventionAction
    data object ClearData : PendingInterventionAction
    data class UpdateColumn(val field: ColumnField, val colIdx: Int) : PendingInterventionAction
    data object ExportBackup : PendingInterventionAction
    data object ImportBackup : PendingInterventionAction
    data object ChangePin : PendingInterventionAction
}

sealed interface InterventionUiEffect {
    data object LaunchExcelPicker : InterventionUiEffect
    data object LaunchBackupExport : InterventionUiEffect
    data object LaunchBackupImport : InterventionUiEffect
}

data class SecurityUiState(
    val isInterventionUnlocked: Boolean = false,
    val showInterventionDialog: Boolean = false,
    val showSecurityCenterDialog: Boolean = false,
    val pendingActionLabel: String? = null,
    val masterPasswordError: String? = null,
    val masterCooldownSecondsLeft: Int = 0,
    val integrityReport: SecurityIntegrityReport? = null,
    val tamperAlertActive: Boolean = false,
)

class TexnonezaretViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = TexnonezaretStorage(application)

    private val _pinState = MutableStateFlow(PinUiState())
    val pinState: StateFlow<PinUiState> = _pinState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState(themeMode = storage.getThemeMode()))
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _securityState = MutableStateFlow(SecurityUiState())
    val securityState: StateFlow<SecurityUiState> = _securityState.asStateFlow()

    private val _effects = MutableSharedFlow<InterventionUiEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<InterventionUiEffect> = _effects.asSharedFlow()

    private var pinBuffer: String = ""
    private var tempNewPin: String = ""
    private var cooldownJob: Job? = null
    private var masterCooldownJob: Job? = null
    private var pendingAction: PendingInterventionAction? = null
    private var lastActivityElapsedMs: Long = System.currentTimeMillis()
    private val idleTimeoutMs: Long = 5 * 60 * 1000L

    init {
        runSecurityAudit()
        val savedHash = storage.getSavedPinHash()
        if (savedHash == null) {
            openPinScreen(PinFlowMode.SETUP)
        } else {
            openPinScreen(PinFlowMode.ENTER)
        }
        loadPersistedSheet()
    }

    fun runSecurityAudit() {
        val prefsOk = storage.verifySecurityPrefsIntegrity()
        val sheetTampered = storage.isSheetFileTampered()
        val report = SecurityGuard.inspectApkIntegrity(
            context = getApplication(),
            sealedSignerSha256 = storage.getSealedSignerSha256(),
            onSealFirstSigner = { sig -> storage.saveSealedSignerSha256(sig) },
            storageIntegrityOk = prefsOk && !sheetTampered,
        )
        _securityState.update { cur ->
            cur.copy(
                integrityReport = report,
                tamperAlertActive = report.hasCriticalTamper && !cur.isInterventionUnlocked,
            )
        }
    }

    private fun loadPersistedSheet() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { storage.loadSheetData() }
            if (loaded != null && loaded.raw.isNotEmpty()) {
                applyLoadedSheet(loaded, expandIfNeeded = false)
            }
        }
    }

    fun recordUserActivity() {
        lastActivityElapsedMs = System.currentTimeMillis()
    }

    fun checkIdleLockOnForeground() {
        runSecurityAudit()
        val hasPin = storage.getSavedPinHash() != null
        if (hasPin && !_pinState.value.isLocked) {
            if (System.currentTimeMillis() - lastActivityElapsedMs >= idleTimeoutMs) {
                lockNow()
            }
        }
    }

    fun openPinScreen(mode: PinFlowMode) {
        pinBuffer = ""
        if (mode == PinFlowMode.SETUP) {
            tempNewPin = ""
        }
        _pinState.update {
            it.copy(
                isLocked = true,
                mode = mode,
                enteredDigits = 0,
                message = null,
            )
        }
        if (mode == PinFlowMode.ENTER) {
            syncCooldownFromStorage()
        } else {
            cooldownJob?.cancel()
            _pinState.update { it.copy(cooldownSecondsLeft = 0) }
        }
    }

    fun lockNow() {
        _securityState.update {
            it.copy(
                isInterventionUnlocked = false,
                showInterventionDialog = false,
                showSecurityCenterDialog = false,
            )
        }
        pendingAction = null
        if (storage.getSavedPinHash() != null) {
            openPinScreen(PinFlowMode.ENTER)
        } else {
            openPinScreen(PinFlowMode.SETUP)
        }
    }

    fun cancelPinChange() {
        if (storage.getSavedPinHash() != null) {
            unlockApp()
        }
    }

    // -------------------------------------------------------------------------
    // Intervention Password (Master Override) & Tamper Defense
    // -------------------------------------------------------------------------

    fun openSecurityCenter() {
        recordUserActivity()
        runSecurityAudit()
        _securityState.update { it.copy(showSecurityCenterDialog = true) }
    }

    fun dismissSecurityCenter() {
        _securityState.update { it.copy(showSecurityCenterDialog = false) }
    }

    fun lockInterventionMode() {
        recordUserActivity()
        pendingAction = null
        _securityState.update {
            it.copy(
                isInterventionUnlocked = false,
                showInterventionDialog = false,
                pendingActionLabel = null,
                masterPasswordError = null,
            )
        }
        _searchState.update {
            it.copy(statusBanner = "Müdaxilə kilidi bağlandı. Məlumatlar yalnız oxumaq üçündür.")
        }
    }

    fun requestProtectedAction(action: PendingInterventionAction) {
        recordUserActivity()
        val sec = _securityState.value
        if (sec.tamperAlertActive && !sec.isInterventionUnlocked) {
            pendingAction = action
            openMasterPasswordDialog("Təhlükəsizlik kilidi aktivdir")
            return
        }
        if (sec.isInterventionUnlocked) {
            executeInterventionAction(action)
        } else {
            pendingAction = action
            openMasterPasswordDialog(actionLabel(action))
        }
    }

    fun dismissInterventionDialog() {
        pendingAction = null
        _securityState.update {
            it.copy(
                showInterventionDialog = false,
                pendingActionLabel = null,
                masterPasswordError = null,
            )
        }
    }

    fun submitInterventionPassword(password: String) {
        recordUserActivity()
        if (isMasterUnderCooldown()) {
            syncMasterCooldownFromStorage()
            return
        }
        if (SecurityGuard.verifyInterventionPassword(password)) {
            storage.setMasterFailCount(0)
            storage.setMasterLockUntilMillis(0L)
            masterCooldownJob?.cancel()
            storage.refreshSecurityIntegritySeal()
            val actionToRun = pendingAction
            pendingAction = null
            _securityState.update {
                it.copy(
                    isInterventionUnlocked = true,
                    showInterventionDialog = false,
                    pendingActionLabel = null,
                    masterPasswordError = null,
                    masterCooldownSecondsLeft = 0,
                    tamperAlertActive = false,
                )
            }
            if (actionToRun != null && actionToRun != PendingInterventionAction.UnlockModeOnly) {
                executeInterventionAction(actionToRun)
            } else {
                _searchState.update {
                    it.copy(statusBanner = "Müdaxilə icazəsi aktivləşdirildi.")
                }
            }
        } else {
            val fails = storage.getMasterFailCount() + 1
            storage.setMasterFailCount(fails)
            if (fails >= 5) {
                storage.setMasterLockUntilMillis(System.currentTimeMillis() + 30_000L)
                syncMasterCooldownFromStorage()
            } else {
                _securityState.update {
                    it.copy(
                        masterPasswordError = "Müdaxilə parolu yanlışdır! (${5 - fails} cəhd qalıb)",
                    )
                }
            }
        }
    }

    fun resetPinWithInterventionPassword(masterPassword: String, wipeData: Boolean): Boolean {
        recordUserActivity()
        if (isMasterUnderCooldown()) {
            syncMasterCooldownFromStorage()
            return false
        }
        if (!SecurityGuard.verifyInterventionPassword(masterPassword)) {
            val fails = storage.getMasterFailCount() + 1
            storage.setMasterFailCount(fails)
            if (fails >= 5) {
                storage.setMasterLockUntilMillis(System.currentTimeMillis() + 30_000L)
                syncMasterCooldownFromStorage()
            } else {
                _securityState.update {
                    it.copy(
                        masterPasswordError = "Müdaxilə parolu yanlışdır! (${5 - fails} cəhd qalıb)",
                    )
                }
            }
            return false
        }

        storage.setMasterFailCount(0)
        storage.setMasterLockUntilMillis(0L)
        masterCooldownJob?.cancel()
        if (wipeData) {
            storage.clearSheetData()
            _searchState.update { SearchUiState(themeMode = it.themeMode) }
        }
        storage.clearPinAndSecurity()
        tempNewPin = ""
        pinBuffer = ""
        cooldownJob?.cancel()
        _securityState.update {
            it.copy(
                isInterventionUnlocked = true,
                masterPasswordError = null,
                masterCooldownSecondsLeft = 0,
                tamperAlertActive = false,
            )
        }
        openPinScreen(PinFlowMode.SETUP)
        return true
    }

    fun clearTamperLockWithPassword(masterPassword: String): Boolean {
        if (!SecurityGuard.verifyInterventionPassword(masterPassword)) {
            val fails = storage.getMasterFailCount() + 1
            storage.setMasterFailCount(fails)
            if (fails >= 5) {
                storage.setMasterLockUntilMillis(System.currentTimeMillis() + 30_000L)
                syncMasterCooldownFromStorage()
            } else {
                _securityState.update {
                    it.copy(
                        masterPasswordError = "Müdaxilə parolu yanlışdır! (${5 - fails} cəhd qalıb)",
                    )
                }
            }
            return false
        }
        storage.setMasterFailCount(0)
        storage.setMasterLockUntilMillis(0L)
        storage.refreshSecurityIntegritySeal()
        _securityState.update {
            it.copy(
                isInterventionUnlocked = true,
                tamperAlertActive = false,
                masterPasswordError = null,
                masterCooldownSecondsLeft = 0,
            )
        }
        return true
    }

    private fun openMasterPasswordDialog(label: String) {
        _securityState.update {
            it.copy(
                showInterventionDialog = true,
                pendingActionLabel = label,
                masterPasswordError = null,
            )
        }
        syncMasterCooldownFromStorage()
    }

    private fun actionLabel(action: PendingInterventionAction): String = when (action) {
        PendingInterventionAction.UnlockModeOnly -> "Müdaxilə rejimini açmaq"
        PendingInterventionAction.ImportExcel -> "Yeni Excel/CSV faylı yükləmək"
        PendingInterventionAction.ClearData -> "Yaddaşdakı məlumatları təmizləmək"
        is PendingInterventionAction.UpdateColumn -> "Sütun uyğunluğunu dəyişdirmək"
        PendingInterventionAction.ExportBackup -> "Ehtiyat nüsxə ixrac etmək"
        PendingInterventionAction.ImportBackup -> "Ehtiyat nüsxədən məlumat yükləmək"
        PendingInterventionAction.ChangePin -> "Tətbiq PIN şifrəsini dəyişmək"
    }

    private fun executeInterventionAction(action: PendingInterventionAction) {
        when (action) {
            PendingInterventionAction.UnlockModeOnly -> Unit
            PendingInterventionAction.ImportExcel -> {
                _effects.tryEmit(InterventionUiEffect.LaunchExcelPicker)
            }
            PendingInterventionAction.ClearData -> {
                clearLoadedSpreadsheet()
            }
            is PendingInterventionAction.UpdateColumn -> {
                updateColumnMapping(action.field, action.colIdx)
            }
            PendingInterventionAction.ExportBackup -> {
                if (_searchState.value.rows.isEmpty()) {
                    _searchState.update { it.copy(fileError = "Saxlamaq üçün əvvəlcə Excel yükləyin.") }
                } else {
                    _effects.tryEmit(InterventionUiEffect.LaunchBackupExport)
                }
            }
            PendingInterventionAction.ImportBackup -> {
                _effects.tryEmit(InterventionUiEffect.LaunchBackupImport)
            }
            PendingInterventionAction.ChangePin -> {
                openPinScreen(PinFlowMode.CHANGE_OLD)
            }
        }
    }

    private fun isMasterUnderCooldown(): Boolean = storage.getMasterLockUntilMillis() > System.currentTimeMillis()

    private fun syncMasterCooldownFromStorage() {
        masterCooldownJob?.cancel()
        val until = storage.getMasterLockUntilMillis()
        val now = System.currentTimeMillis()
        if (until <= now) {
            _securityState.update { it.copy(masterCooldownSecondsLeft = 0) }
            return
        }
        masterCooldownJob = viewModelScope.launch {
            while (true) {
                val remMs = storage.getMasterLockUntilMillis() - System.currentTimeMillis()
                if (remMs <= 0L) {
                    storage.setMasterFailCount(0)
                    storage.setMasterLockUntilMillis(0L)
                    _securityState.update {
                        it.copy(masterCooldownSecondsLeft = 0, masterPasswordError = null)
                    }
                    break
                }
                val sec = ((remMs + 999L) / 1000L).toInt()
                _securityState.update {
                    it.copy(
                        masterCooldownSecondsLeft = sec,
                        masterPasswordError = "Çox sayda səhv cəhd. $sec san. gözləyin.",
                    )
                }
                delay(500L)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 4-Digit User PIN Entry
    // -------------------------------------------------------------------------

    fun pressPinDigit(digit: Char) {
        recordUserActivity()
        val cur = _pinState.value
        if (!cur.isLocked) return
        if (cur.mode == PinFlowMode.ENTER && isUnderCooldown()) return
        if (!digit.isDigit() || pinBuffer.length >= 4) return

        pinBuffer += digit
        _pinState.update { it.copy(enteredDigits = pinBuffer.length, message = null) }
        if (pinBuffer.length == 4) {
            handlePinComplete()
        }
    }

    fun pressPinBackspace() {
        recordUserActivity()
        val cur = _pinState.value
        if (!cur.isLocked) return
        if (cur.mode == PinFlowMode.ENTER && isUnderCooldown()) return
        if (pinBuffer.isNotEmpty()) {
            pinBuffer = pinBuffer.dropLast(1)
            _pinState.update { it.copy(enteredDigits = pinBuffer.length) }
        }
    }

    fun pressPinClear() {
        recordUserActivity()
        val cur = _pinState.value
        if (!cur.isLocked) return
        if (cur.mode == PinFlowMode.ENTER && isUnderCooldown()) return
        pinBuffer = ""
        _pinState.update { it.copy(enteredDigits = 0, message = null) }
    }

    private fun handlePinComplete() {
        val entered = pinBuffer
        pinBuffer = ""
        val mode = _pinState.value.mode

        when (mode) {
            PinFlowMode.SETUP -> {
                tempNewPin = entered
                openPinScreen(PinFlowMode.CONFIRM)
            }
            PinFlowMode.CONFIRM -> {
                if (entered == tempNewPin) {
                    storage.savePinHash(TexnonezaretEngine.hashPin(entered))
                    storage.setFailCount(0)
                    storage.setLockUntilMillis(0L)
                    unlockApp()
                } else {
                    tempNewPin = ""
                    _pinState.update {
                        it.copy(
                            enteredDigits = 0,
                            shakeTrigger = it.shakeTrigger + 1,
                            message = "PIN-lər uyğun gəlmədi. Yenidən cəhd edin.",
                        )
                    }
                    viewModelScope.launch {
                        delay(850)
                        if (_pinState.value.isLocked && _pinState.value.mode == PinFlowMode.CONFIRM) {
                            openPinScreen(PinFlowMode.SETUP)
                        }
                    }
                }
            }
            PinFlowMode.CHANGE_OLD -> {
                val saved = storage.getSavedPinHash()
                if (saved != null && TexnonezaretEngine.hashPin(entered) == saved) {
                    openPinScreen(PinFlowMode.SETUP)
                } else {
                    _pinState.update {
                        it.copy(
                            enteredDigits = 0,
                            shakeTrigger = it.shakeTrigger + 1,
                            message = "Cari PIN yanlışdır!",
                        )
                    }
                }
            }
            PinFlowMode.ENTER -> {
                if (isUnderCooldown()) return
                val saved = storage.getSavedPinHash()
                if (saved != null && TexnonezaretEngine.hashPin(entered) == saved) {
                    storage.setFailCount(0)
                    storage.setLockUntilMillis(0L)
                    unlockApp()
                } else {
                    val fails = storage.getFailCount() + 1
                    storage.setFailCount(fails)
                    if (fails >= 5) {
                        storage.setLockUntilMillis(System.currentTimeMillis() + 30_000L)
                        _pinState.update {
                            it.copy(
                                enteredDigits = 0,
                                shakeTrigger = it.shakeTrigger + 1,
                            )
                        }
                        syncCooldownFromStorage()
                    } else {
                        _pinState.update {
                            it.copy(
                                enteredDigits = 0,
                                shakeTrigger = it.shakeTrigger + 1,
                                message = "Yanlış PIN! (${5 - fails} cəhd qalıb)",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun unlockApp() {
        recordUserActivity()
        pinBuffer = ""
        tempNewPin = ""
        cooldownJob?.cancel()
        _pinState.update {
            it.copy(
                isLocked = false,
                enteredDigits = 0,
                message = null,
                cooldownSecondsLeft = 0,
            )
        }
    }

    private fun isUnderCooldown(): Boolean = storage.getLockUntilMillis() > System.currentTimeMillis()

    private fun syncCooldownFromStorage() {
        cooldownJob?.cancel()
        val until = storage.getLockUntilMillis()
        val now = System.currentTimeMillis()
        if (until <= now) {
            _pinState.update { it.copy(cooldownSecondsLeft = 0) }
            return
        }
        cooldownJob = viewModelScope.launch {
            while (true) {
                val remMs = storage.getLockUntilMillis() - System.currentTimeMillis()
                if (remMs <= 0L) {
                    storage.setFailCount(0)
                    storage.setLockUntilMillis(0L)
                    _pinState.update { it.copy(cooldownSecondsLeft = 0, message = null) }
                    break
                }
                val sec = ((remMs + 999L) / 1000L).toInt()
                _pinState.update {
                    it.copy(
                        cooldownSecondsLeft = sec,
                        message = "Çox sayda səhv cəhd. $sec san. gözləyin.",
                    )
                }
                delay(500L)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Spreadsheet Import, Column Mapping, Search, Theme & Signed Backup
    // -------------------------------------------------------------------------

    fun importSpreadsheetUri(uri: Uri) {
        recordUserActivity()
        viewModelScope.launch {
            _searchState.update { it.copy(isLoadingFile = true, fileError = null, statusBanner = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val displayName = resolveDisplayName(uri)
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Fayl oxuna bilmədi")
                    val raw = SpreadsheetParser.parseBytes(bytes, displayName)
                    if (raw.isEmpty()) throw IllegalStateException("Boş cədvəl")
                    val (hdr, map) = TexnonezaretEngine.detectColumns(raw)
                    PersistedSheetData(raw = raw, hdr = hdr, map = map, sourceFileName = displayName)
                }
            }
            result.onSuccess { sheetData ->
                withContext(Dispatchers.IO) { storage.saveSheetData(sheetData) }
                applyLoadedSheet(sheetData, expandIfNeeded = true)
            }.onFailure {
                _searchState.update {
                    it.copy(
                        isLoadingFile = false,
                        fileError = "Faylı oxumaq alınmadı. .xlsx, .xls və ya .csv seçin.",
                    )
                }
            }
        }
    }

    private fun clearLoadedSpreadsheet() {
        recordUserActivity()
        storage.clearSheetData()
        _searchState.update { cur ->
            SearchUiState(
                themeMode = cur.themeMode,
                statusBanner = "Yaddaşdakı məlumatlar təmizləndi.",
            )
        }
    }

    private fun updateColumnMapping(field: ColumnField, columnIndex: Int) {
        recordUserActivity()
        val cur = _searchState.value
        val updatedMap = cur.map.withField(field, columnIndex)
        val updatedSheet = PersistedSheetData(
            raw = cur.raw,
            hdr = cur.hdr,
            map = updatedMap,
            sourceFileName = cur.sourceFileName,
        )
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveSheetData(updatedSheet)
        }
        applyLoadedSheet(updatedSheet, expandIfNeeded = false, preserveExpanded = cur.mapPanelExpanded)
    }

    fun toggleColumnMapExpanded() {
        recordUserActivity()
        _searchState.update { it.copy(mapPanelExpanded = !it.mapPanelExpanded) }
    }

    fun updatePlateQuery(query: String) {
        recordUserActivity()
        _searchState.update { cur ->
            val next = cur.copy(
                plateQuery = query,
                nameQuery = "",
                searchMode = SearchMode.PLATE,
            )
            recomputeOutcome(next)
        }
    }

    fun updateNameQuery(query: String) {
        recordUserActivity()
        _searchState.update { cur ->
            val next = cur.copy(
                nameQuery = query,
                plateQuery = "",
                searchMode = SearchMode.NAME,
            )
            recomputeOutcome(next)
        }
    }

    fun selectStatusFilter(filter: StatusFilter) {
        recordUserActivity()
        _searchState.update { cur ->
            recomputeOutcome(cur.copy(statusFilter = filter))
        }
    }

    fun cycleThemeMode() {
        recordUserActivity()
        val nextMode = when (_searchState.value.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        storage.setThemeMode(nextMode)
        _searchState.update { it.copy(themeMode = nextMode) }
    }

    fun buildBackupJson(): String? {
        recordUserActivity()
        if (!_securityState.value.isInterventionUnlocked) return null
        val cur = _searchState.value
        if (cur.raw.isEmpty()) return null
        return storage.serializeBackupEnvelope(
            PersistedSheetData(
                raw = cur.raw,
                hdr = cur.hdr,
                map = cur.map,
                sourceFileName = cur.sourceFileName,
            )
        )
    }

    fun importBackupUri(uri: Uri) {
        recordUserActivity()
        if (!_securityState.value.isInterventionUnlocked) {
            requestProtectedAction(PendingInterventionAction.ImportBackup)
            return
        }
        viewModelScope.launch {
            _searchState.update { it.copy(isLoadingFile = true, fileError = null, statusBanner = null) }
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val jsonText = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                        ?: throw IllegalStateException("Fayl oxunmadı")
                    storage.deserializeSheetData(jsonText)
                        ?: throw IllegalStateException("Yanlış və ya zədələnmiş ehtiyat nüsxə faylı")
                }
            }
            loaded.onSuccess { sheetData ->
                withContext(Dispatchers.IO) { storage.saveSheetData(sheetData) }
                applyLoadedSheet(sheetData, expandIfNeeded = false)
                _searchState.update { it.copy(statusBanner = "Ehtiyat nüsxə uğurla bərpa olundu.") }
            }.onFailure {
                _searchState.update {
                    it.copy(
                        isLoadingFile = false,
                        fileError = "Ehtiyat nüsxə faylı oxuna bilmədi və ya bütövlüyü pozulub.",
                    )
                }
            }
        }
    }

    fun clearStatusBanner() {
        _searchState.update { it.copy(statusBanner = null, fileError = null) }
    }

    private fun applyLoadedSheet(
        data: PersistedSheetData,
        expandIfNeeded: Boolean,
        preserveExpanded: Boolean? = null,
    ) {
        val rows = TexnonezaretEngine.buildRows(data.raw, data.hdr, data.map)
        val colCount = data.raw.take(50).maxOfOrNull { it.size } ?: 0
        val summary = computeSummary(rows)
        val expand = preserveExpanded ?: if (expandIfNeeded) data.map.needsReview() else false
        _searchState.update { cur ->
            val next = cur.copy(
                isLoadingFile = false,
                fileError = null,
                sourceFileName = data.sourceFileName,
                raw = data.raw,
                hdr = data.hdr,
                map = data.map,
                columnCount = colCount,
                rows = rows,
                summary = summary,
                mapPanelExpanded = expand,
            )
            recomputeOutcome(next)
        }
    }

    private fun computeSummary(rows: List<VehicleRow>): StatusSummaryCounts {
        if (rows.isEmpty()) return StatusSummaryCounts()
        val today = TexnonezaretEngine.todayUtcMidnightMillis()
        var valid = 0
        var soon = 0
        var overdue = 0
        var none = 0
        for (r in rows) {
            when (TexnonezaretEngine.evaluateStatus(r.inspectionUtcMillis, today).kind) {
                InspectionStatusKind.VALID -> valid++
                InspectionStatusKind.SOON, InspectionStatusKind.TODAY -> soon++
                InspectionStatusKind.OVERDUE -> overdue++
                InspectionStatusKind.NONE -> none++
            }
        }
        return StatusSummaryCounts(
            total = rows.size,
            valid = valid,
            soon = soon,
            overdue = overdue,
            none = none,
        )
    }

    private fun recomputeOutcome(state: SearchUiState): SearchUiState {
        val outcome = TexnonezaretEngine.searchRows(
            rows = state.rows,
            plateQuery = state.plateQuery,
            nameQuery = state.nameQuery,
            mode = state.searchMode,
            filter = state.statusFilter,
        )
        return state.copy(outcome = outcome)
    }

    private fun resolveDisplayName(uri: Uri): String = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return@runCatching cursor.getString(idx).orEmpty()
                }
            }
        uri.lastPathSegment.orEmpty()
    }.getOrDefault("")
}
