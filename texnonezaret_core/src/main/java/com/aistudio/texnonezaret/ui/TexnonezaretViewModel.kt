package com.aistudio.texnonezaret.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.os.Build
import com.aistudio.texnonezaret.data.PersistedSheetData
import com.aistudio.texnonezaret.data.SpreadsheetParser
import com.aistudio.texnonezaret.data.TexnonezaretStorage
import com.aistudio.texnonezaret.data.ThemeMode
import com.aistudio.texnonezaret.data.TntCapturedPhoto
import com.aistudio.texnonezaret.data.TntEncryptedJpgCodec
import com.aistudio.texnonezaret.data.TntLocationHelper
import com.aistudio.texnonezaret.data.TntPhotoExporter
import com.aistudio.texnonezaret.data.TntPhotoProcessor
import com.aistudio.texnonezaret.data.TntPhotoVault
import com.aistudio.texnonezaret.data.TntPhotoWatermarkFormatter
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
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
    data object OpenSecurityCenter : PendingInterventionAction
    data object ImportExcel : PendingInterventionAction
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

enum class TntBottomTab {
    SEARCH,
    GALLERY,
}

data class DraftImportedPhoto(
    val tempFile: File,
    val capturedAtMillis: Long,
    val timestampText: String,
    val latitudeText: String,
    val longitudeText: String,
    val plateTag: String,
    val hasExifTime: Boolean = false,
    val hasExifGps: Boolean = false,
    val isFetchingGps: Boolean = false,
)

data class PendingEncryptedJpgImport(
    val fileBytes: ByteArray,
    val errorMessage: String? = null,
)

data class PendingSafExport(
    val fileName: String,
    val bytes: ByteArray,
)

data class PhotoVaultUiState(
    val activeTab: TntBottomTab = TntBottomTab.SEARCH,
    val photos: List<TntCapturedPhoto> = emptyList(),
    val isProcessingPhoto: Boolean = false,
    val selectedPhoto: TntCapturedPhoto? = null,
    val pendingGalleryImport: DraftImportedPhoto? = null,
    val pendingEncryptedImportPrompt: PendingEncryptedJpgImport? = null,
    val pendingSafSaveBytes: PendingSafExport? = null,
    val vaultMessage: String? = null,
    val vaultError: String? = null,
)

data class SecurityUiState(
    val isInterventionUnlocked: Boolean = false,
    val showInterventionDialog: Boolean = false,
    val showSecurityCenterDialog: Boolean = false,
    val pendingActionLabel: String? = null,
    val masterPasswordError: String? = null,
    val masterCooldownSecondsLeft: Int = 0,
    val integrityReport: SecurityIntegrityReport? = null,
    val tamperAlertActive: Boolean = false,
    val immediateBgLock: Boolean = false,
)

class TexnonezaretViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = TexnonezaretStorage(application)
    private val photoVault = TntPhotoVault(application)

    private val _pinState = MutableStateFlow(PinUiState())
    val pinState: StateFlow<PinUiState> = _pinState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState(themeMode = storage.getThemeMode()))
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _securityState = MutableStateFlow(
        SecurityUiState(immediateBgLock = storage.isImmediateBgLockEnabled())
    )
    val securityState: StateFlow<SecurityUiState> = _securityState.asStateFlow()

    private val _vaultState = MutableStateFlow(PhotoVaultUiState())
    val vaultState: StateFlow<PhotoVaultUiState> = _vaultState.asStateFlow()

    private val _effects = MutableSharedFlow<InterventionUiEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<InterventionUiEffect> = _effects.asSharedFlow()

    private var pinBuffer: String = ""
    private var tempNewPin: String = ""
    private var cooldownJob: Job? = null
    private var masterCooldownJob: Job? = null
    private var pendingAction: PendingInterventionAction? = null
    private var lastActivityElapsedMs: Long = System.currentTimeMillis()
    private val idleTimeoutMs: Long = 10 * 60 * 1000L
    private var isExternalCapturePending: Boolean = false

    companion object {
        private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024
    }

    init {
        runSecurityAudit()
        val savedHash = storage.getSavedPinHash()
        if (savedHash == null) {
            openPinScreen(PinFlowMode.SETUP)
        } else {
            openPinScreen(PinFlowMode.ENTER)
        }
        loadPersistedSheet()
        loadVaultPhotos()
    }

    fun runSecurityAudit() {
        val prefsOk = storage.verifySecurityPrefsIntegrity()
        val sheetTampered = storage.isSheetFileTampered()
        val vaultTampered = photoVault.isVaultTampered()
        val report = SecurityGuard.inspectApkIntegrity(
            context = getApplication(),
            sealedSignerSha256 = storage.getSealedSignerSha256(),
            onSealFirstSigner = { sig -> storage.saveSealedSignerSha256(sig) },
            storageIntegrityOk = prefsOk && !sheetTampered,
            photoVaultIntegrityOk = !vaultTampered,
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

    private fun loadVaultPhotos() {
        viewModelScope.launch {
            val items = photoVault.loadPhotos()
            _vaultState.update { it.copy(photos = items) }
        }
    }

    fun recordUserActivity() {
        lastActivityElapsedMs = System.currentTimeMillis()
    }

    fun markExternalCapturePending(pending: Boolean) {
        isExternalCapturePending = pending
        recordUserActivity()
    }

    fun onAppBackgrounded() {
        if (!isExternalCapturePending) {
            _securityState.update {
                it.copy(
                    isInterventionUnlocked = false,
                    showInterventionDialog = false,
                    showSecurityCenterDialog = false,
                    pendingActionLabel = null,
                    masterPasswordError = null,
                )
            }
            pendingAction = null
            if (_securityState.value.immediateBgLock &&
                storage.getSavedPinHash() != null &&
                !_pinState.value.isLocked
            ) {
                lockNow()
            }
        }
    }

    fun checkIdleLockOnForeground() {
        runSecurityAudit()
        val hasPin = storage.getSavedPinHash() != null
        if (hasPin && !_pinState.value.isLocked && !isExternalCapturePending) {
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
        if (_securityState.value.isInterventionUnlocked) {
            _securityState.update { it.copy(showSecurityCenterDialog = true) }
        } else {
            pendingAction = PendingInterventionAction.OpenSecurityCenter
            openMasterPasswordDialog(actionLabel(PendingInterventionAction.OpenSecurityCenter))
        }
    }

    fun dismissSecurityCenter() {
        _securityState.update { it.copy(showSecurityCenterDialog = false) }
    }

    fun toggleImmediateBgLock(enabled: Boolean) {
        recordUserActivity()
        if (!_securityState.value.isInterventionUnlocked) return
        storage.setImmediateBgLockEnabled(enabled)
        _securityState.update { it.copy(immediateBgLock = enabled) }
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
        if (action is PendingInterventionAction.UpdateColumn) {
            executeInterventionAction(action)
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
        PendingInterventionAction.OpenSecurityCenter -> "Təhlükəsizlik qalxanının məlumatlarını görmək"
        PendingInterventionAction.ImportExcel -> "Yeni Excel/CSV faylı yükləmək"
        is PendingInterventionAction.UpdateColumn -> "Sütun uyğunluğunu dəyişdirmək"
        PendingInterventionAction.ExportBackup -> "Ehtiyat nüsxə ixrac etmək"
        PendingInterventionAction.ImportBackup -> "Ehtiyat nüsxədən məlumat yükləmək"
        PendingInterventionAction.ChangePin -> "Tətbiq PIN şifrəsini dəyişmək"
    }

    private fun executeInterventionAction(action: PendingInterventionAction) {
        when (action) {
            PendingInterventionAction.UnlockModeOnly -> Unit
            PendingInterventionAction.OpenSecurityCenter -> {
                runSecurityAudit()
                _securityState.update { it.copy(showSecurityCenterDialog = true) }
            }
            PendingInterventionAction.ImportExcel -> {
                _effects.tryEmit(InterventionUiEffect.LaunchExcelPicker)
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
                    storage.savePinHash(TexnonezaretEngine.hashPinStrong(entered))
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
                if (saved != null && TexnonezaretEngine.verifyPinHash(entered, saved)) {
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
                if (saved != null && TexnonezaretEngine.verifyPinHash(entered, saved)) {
                    if (!TexnonezaretEngine.isStrongPinHash(saved)) {
                        storage.savePinHash(TexnonezaretEngine.hashPinStrong(entered))
                    }
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
                    val bytes = resolver.openInputStream(uri)?.use { readBoundedBytes(it, MAX_IMPORT_BYTES) }
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

    fun clearLoadedSpreadsheet() {
        recordUserActivity()
        storage.clearSheetData()
        _searchState.update { cur ->
            SearchUiState(
                themeMode = cur.themeMode,
                statusBanner = "Yaddaşdakı məlumatlar təmizləndi.",
            )
        }
    }

    fun updateColumnMapping(field: ColumnField, columnIndex: Int) {
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
                    val bytes = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.use { readBoundedBytes(it, MAX_IMPORT_BYTES) }
                        ?: throw IllegalStateException("Fayl oxunmadı")
                    val jsonText = String(bytes, Charsets.UTF_8)
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

    // -------------------------------------------------------------------------
    // Encrypted App-Internal Photo Vault & Telemetry Stamping
    // -------------------------------------------------------------------------

    fun setActiveTab(tab: TntBottomTab) {
        recordUserActivity()
        _vaultState.update { it.copy(activeTab = tab, vaultError = null) }
    }

    fun openPhotoPreview(photo: TntCapturedPhoto) {
        recordUserActivity()
        _vaultState.update { it.copy(selectedPhoto = photo) }
    }

    fun closePhotoPreview() {
        recordUserActivity()
        _vaultState.update { it.copy(selectedPhoto = null) }
    }

    fun clearVaultBanner() {
        _vaultState.update { it.copy(vaultMessage = null, vaultError = null) }
    }

    fun processAndSaveCapturedPhoto(
        tempFile: File,
        plateTagOverride: String? = null,
    ) {
        recordUserActivity()
        isExternalCapturePending = false
        viewModelScope.launch {
            _vaultState.update {
                it.copy(isProcessingPhoto = true, vaultError = null, vaultMessage = null)
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val capturedAt = System.currentTimeMillis()
                    val fixDeferred = async { TntLocationHelper.acquireBestFix(app) }
                    val fix = fixDeferred.await()

                    val tsText = TntPhotoWatermarkFormatter.formatTimestamp(capturedAt)
                    val stripCoord = TntPhotoWatermarkFormatter.formatCompactCoordinatesForStrip(
                        latitude = fix?.latitude,
                        longitude = fix?.longitude,
                    )
                    val fullCoord = TntPhotoWatermarkFormatter.formatCoordinates(
                        latitude = fix?.latitude,
                        longitude = fix?.longitude,
                        accuracyMeters = fix?.accuracyMeters,
                    )
                    val activePlate = (plateTagOverride ?: _searchState.value.plateQuery)
                        .let { TexnonezaretEngine.normPlate(it) }

                    val jpegBytes = TntPhotoProcessor.processAndStampPhoto(
                        sourceFile = tempFile,
                        timestampText = tsText,
                        coordinatesStripText = stripCoord,
                        plateTag = activePlate,
                    )
                    photoVault.saveStampedPhoto(
                        jpegBytes = jpegBytes,
                        capturedAtMillis = capturedAt,
                        timestampText = tsText,
                        latitude = fix?.latitude,
                        longitude = fix?.longitude,
                        accuracyMeters = fix?.accuracyMeters,
                        coordinatesText = fullCoord,
                        plateTag = activePlate,
                    )
                }.also {
                    runCatching { if (tempFile.exists()) tempFile.delete() }
                }
            }

            result.onSuccess { saved ->
                val updated = photoVault.loadPhotos()
                _vaultState.update {
                    it.copy(
                        photos = updated,
                        activeTab = TntBottomTab.GALLERY,
                        isProcessingPhoto = false,
                        vaultMessage = "Şəkil şifrələndi və TNT qalereyasında saxlanıldı (${saved.timestampText}).",
                        vaultError = null,
                    )
                }
            }.onFailure { err ->
                _vaultState.update {
                    it.copy(
                        isProcessingPhoto = false,
                        vaultError = err.message ?: "Şəkil qorunan qalereyaya yazıla bilmədi.",
                    )
                }
            }
        }
    }

    suspend fun loadDecryptedPhotoBytes(photo: TntCapturedPhoto): ByteArray? =
        photoVault.loadDecryptedPhotoBytes(photo)

    fun deleteVaultPhoto(photoId: String) {
        recordUserActivity()
        viewModelScope.launch {
            val remaining = photoVault.deletePhoto(photoId)
            _vaultState.update { cur ->
                cur.copy(
                    photos = remaining,
                    selectedPhoto = if (cur.selectedPhoto?.id == photoId) null else cur.selectedPhoto,
                    vaultMessage = "Şəkil qorunan qalereyadan silindi.",
                )
            }
        }
    }

    fun importPhotoFromUri(uri: Uri) {
        recordUserActivity()
        isExternalCapturePending = false
        viewModelScope.launch {
            _vaultState.update {
                it.copy(
                    isProcessingPhoto = true,
                    vaultError = null,
                    vaultMessage = null,
                )
            }
            val app = getApplication<Application>()
            val bytesResult = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { stream ->
                        readBoundedBytes(stream, MAX_IMPORT_BYTES)
                    } ?: throw IllegalStateException("Şəkil faylı açıla bilmədi.")
                }
            }
            val bytes = bytesResult.getOrElse { err ->
                _vaultState.update {
                    it.copy(
                        isProcessingPhoto = false,
                        vaultError = err.message ?: "Şəkil oxuna bilmədi.",
                    )
                }
                return@launch
            }

            val encryptedInspection = withContext(Dispatchers.Default) {
                TntEncryptedJpgCodec.inspectEncryptedJpg(bytes)
            }
            if (encryptedInspection != null) {
                if (encryptedInspection.requiresPassword) {
                    _vaultState.update {
                        it.copy(
                            isProcessingPhoto = false,
                            pendingEncryptedImportPrompt = PendingEncryptedJpgImport(fileBytes = bytes),
                        )
                    }
                    return@launch
                } else {
                    val decoded = withContext(Dispatchers.IO) {
                        TntEncryptedJpgCodec.decodeEncryptedJpgEnvelope(bytes)
                    }
                    if (decoded == null) {
                        _vaultState.update {
                            it.copy(
                                isProcessingPhoto = false,
                                vaultError = "Bu şifrəli JPG yalnız yaradıldığı cihazın açarı ilə açıla bilər.",
                            )
                        }
                        return@launch
                    }
                    val savedResult = withContext(Dispatchers.IO) {
                        runCatching {
                            photoVault.saveStampedPhoto(
                                jpegBytes = decoded.stampedJpegBytes,
                                capturedAtMillis = decoded.capturedAtMillis,
                                timestampText = decoded.timestampText,
                                latitude = decoded.latitude,
                                longitude = decoded.longitude,
                                accuracyMeters = decoded.accuracyMeters,
                                coordinatesText = decoded.coordinatesText,
                                plateTag = decoded.plateTag,
                            )
                        }
                    }
                    savedResult.onSuccess {
                        val updated = photoVault.loadPhotos()
                        _vaultState.update {
                            it.copy(
                                photos = updated,
                                activeTab = TntBottomTab.GALLERY,
                                isProcessingPhoto = false,
                                vaultMessage = "Şifrəli JPG deşifrə edildi və TNT qalereyasına əlavə olundu.",
                                vaultError = null,
                            )
                        }
                    }.onFailure { err ->
                        _vaultState.update {
                            it.copy(
                                isProcessingPhoto = false,
                                vaultError = err.message ?: "Şifrəli şəkil qalereyaya yazıla bilmədi.",
                            )
                        }
                    }
                    return@launch
                }
            }

            val draftResult = withContext(Dispatchers.IO) {
                runCatching {
                    val importDir = File(app.cacheDir, "import_tmp").apply { mkdirs() }
                    val tempFile = File(importDir, "tnt_import_${System.currentTimeMillis()}.jpg")
                    tempFile.writeBytes(bytes)
                    val exif = TntPhotoProcessor.extractExifHints(tempFile)
                    val capturedAt = exif.timestampMillis ?: System.currentTimeMillis()
                    val fix = if (exif.latitude == null || exif.longitude == null) {
                        TntLocationHelper.acquireBestFix(app, timeoutMs = 1_600L)
                    } else {
                        null
                    }
                    val lat = exif.latitude ?: fix?.latitude
                    val lon = exif.longitude ?: fix?.longitude
                    val activePlate = _searchState.value.plateQuery
                        .let { TexnonezaretEngine.normPlate(it) }
                    DraftImportedPhoto(
                        tempFile = tempFile,
                        capturedAtMillis = capturedAt,
                        timestampText = TntPhotoWatermarkFormatter.formatTimestamp(capturedAt),
                        latitudeText = lat?.let { String.format(Locale.US, "%.5f", it) }.orEmpty(),
                        longitudeText = lon?.let { String.format(Locale.US, "%.5f", it) }.orEmpty(),
                        plateTag = activePlate,
                        hasExifTime = exif.timestampMillis != null,
                        hasExifGps = exif.latitude != null && exif.longitude != null,
                    )
                }
            }

            draftResult.onSuccess { draft ->
                val previousDraft = _vaultState.value.pendingGalleryImport
                if (previousDraft != null && previousDraft.tempFile != draft.tempFile) {
                    runCatching { previousDraft.tempFile.delete() }
                }
                _vaultState.update {
                    it.copy(
                        isProcessingPhoto = false,
                        activeTab = TntBottomTab.GALLERY,
                        pendingGalleryImport = draft,
                        vaultError = null,
                    )
                }
            }.onFailure { err ->
                _vaultState.update {
                    it.copy(
                        isProcessingPhoto = false,
                        vaultError = err.message ?: "Qalereya şəkli hazırlana bilmədi.",
                    )
                }
            }
        }
    }

    fun submitEncryptedJpgPassword(password: String) {
        recordUserActivity()
        val prompt = _vaultState.value.pendingEncryptedImportPrompt ?: return
        if (password.isBlank()) {
            _vaultState.update {
                it.copy(
                    pendingEncryptedImportPrompt = prompt.copy(
                        errorMessage = "Şifrəli şəkli açmaq üçün parolu daxil edin.",
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            _vaultState.update { it.copy(isProcessingPhoto = true, vaultError = null) }
            val decoded = withContext(Dispatchers.IO) {
                TntEncryptedJpgCodec.decodeEncryptedJpgEnvelope(prompt.fileBytes, password)
            }
            if (decoded == null) {
                _vaultState.update {
                    it.copy(
                        isProcessingPhoto = false,
                        pendingEncryptedImportPrompt = prompt.copy(
                            errorMessage = "Parol yanlışdır və ya şifrəli fayl zədələnib.",
                        ),
                    )
                }
                return@launch
            }
            val savedResult = withContext(Dispatchers.IO) {
                runCatching {
                    photoVault.saveStampedPhoto(
                        jpegBytes = decoded.stampedJpegBytes,
                        capturedAtMillis = decoded.capturedAtMillis,
                        timestampText = decoded.timestampText,
                        latitude = decoded.latitude,
                        longitude = decoded.longitude,
                        accuracyMeters = decoded.accuracyMeters,
                        coordinatesText = decoded.coordinatesText,
                        plateTag = decoded.plateTag,
                    )
                }
            }
            savedResult.onSuccess {
                val updated = photoVault.loadPhotos()
                _vaultState.update {
                    it.copy(
                        photos = updated,
                        activeTab = TntBottomTab.GALLERY,
                        isProcessingPhoto = false,
                        pendingEncryptedImportPrompt = null,
                        vaultMessage = "Şifrəli JPG açıldı və qorunan qalereyada saxlanıldı.",
                        vaultError = null,
                    )
                }
            }.onFailure { err ->
                _vaultState.update {
                    it.copy(
                        isProcessingPhoto = false,
                        pendingEncryptedImportPrompt = null,
                        vaultError = err.message ?: "Şəkil qorunan qalereyaya saxlanıla bilmədi.",
                    )
                }
            }
        }
    }

    fun cancelEncryptedJpgPasswordPrompt() {
        recordUserActivity()
        _vaultState.update { it.copy(pendingEncryptedImportPrompt = null) }
    }

    fun refreshDraftImportGps() {
        recordUserActivity()
        val draft = _vaultState.value.pendingGalleryImport ?: return
        viewModelScope.launch {
            _vaultState.update { cur ->
                cur.copy(pendingGalleryImport = cur.pendingGalleryImport?.copy(isFetchingGps = true))
            }
            val fix = withContext(Dispatchers.IO) {
                TntLocationHelper.acquireBestFix(getApplication(), timeoutMs = 3_000L)
            }
            _vaultState.update { cur ->
                val activeDraft = cur.pendingGalleryImport ?: return@update cur
                if (fix != null) {
                    cur.copy(
                        pendingGalleryImport = activeDraft.copy(
                            latitudeText = String.format(Locale.US, "%.5f", fix.latitude),
                            longitudeText = String.format(Locale.US, "%.5f", fix.longitude),
                            isFetchingGps = false,
                        ),
                        vaultError = null,
                    )
                } else {
                    cur.copy(
                        pendingGalleryImport = activeDraft.copy(isFetchingGps = false),
                        vaultError = "GPS koordinatı alına bilmədi, əl ilə daxil edə bilərsiniz.",
                    )
                }
            }
        }
    }

    fun cancelGalleryPhotoImport() {
        recordUserActivity()
        val draft = _vaultState.value.pendingGalleryImport
        _vaultState.update { it.copy(pendingGalleryImport = null) }
        if (draft != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { if (draft.tempFile.exists()) draft.tempFile.delete() }
            }
        }
    }

    fun confirmGalleryPhotoImport(
        timestampText: String,
        latitudeText: String,
        longitudeText: String,
        plateTag: String,
    ) {
        recordUserActivity()
        val draft = _vaultState.value.pendingGalleryImport ?: return
        viewModelScope.launch {
            _vaultState.update {
                it.copy(
                    isProcessingPhoto = true,
                    pendingGalleryImport = null,
                    vaultError = null,
                    vaultMessage = null,
                )
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val cleanTs = timestampText.trim().ifBlank { draft.timestampText }
                    val parsedEpoch = runCatching {
                        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).parse(cleanTs)?.time
                    }.getOrNull() ?: draft.capturedAtMillis

                    val rawLat = latitudeText.trim().replace(',', '.').toDoubleOrNull()
                    val rawLon = longitudeText.trim().replace(',', '.').toDoubleOrNull()
                    val validCoords = rawLat != null && rawLon != null &&
                        rawLat.isFinite() && rawLon.isFinite() &&
                        rawLat in -90.0..90.0 && rawLon in -180.0..180.0
                    val lat = if (validCoords) rawLat else null
                    val lon = if (validCoords) rawLon else null

                    val stripCoord = TntPhotoWatermarkFormatter.formatCompactCoordinatesForStrip(lat, lon)
                    val fullCoord = TntPhotoWatermarkFormatter.formatCoordinates(lat, lon, null)
                    val cleanTag = plateTag.trim().uppercase(Locale.ROOT).take(24)

                    val stampedBytes = TntPhotoProcessor.processAndStampPhoto(
                        sourceFile = draft.tempFile,
                        timestampText = cleanTs,
                        coordinatesStripText = stripCoord,
                        plateTag = cleanTag,
                    )
                    photoVault.saveStampedPhoto(
                        jpegBytes = stampedBytes,
                        capturedAtMillis = parsedEpoch,
                        timestampText = cleanTs,
                        latitude = lat,
                        longitude = lon,
                        accuracyMeters = null,
                        coordinatesText = fullCoord,
                        plateTag = cleanTag,
                    )
                }.also {
                    runCatching { if (draft.tempFile.exists()) draft.tempFile.delete() }
                }
            }

            result.onSuccess { saved ->
                val updated = photoVault.loadPhotos()
                _vaultState.update {
                    it.copy(
                        photos = updated,
                        activeTab = TntBottomTab.GALLERY,
                        isProcessingPhoto = false,
                        vaultMessage = "Qalereya şəkli məlumatlarla damğalandı, şifrələndi və saxlanıldı (${saved.timestampText}).",
                        vaultError = null,
                    )
                }
            }.onFailure { err ->
                _vaultState.update {
                    it.copy(
                        isProcessingPhoto = false,
                        vaultError = err.message ?: "Qalereya şəkli şifrələnə bilmədi.",
                    )
                }
            }
        }
    }

    fun downloadEncryptedJpg(photo: TntCapturedPhoto, optionalPassword: String?) {
        recordUserActivity()
        viewModelScope.launch {
            _vaultState.update { it.copy(isProcessingPhoto = true, vaultError = null, vaultMessage = null) }
            val app = getApplication<Application>()
            val password = optionalPassword?.trim()?.takeIf { it.isNotEmpty() }
            val exportRes = withContext(Dispatchers.IO) {
                runCatching {
                    val plainBytes = photoVault.loadDecryptedPhotoBytes(photo)
                        ?: throw IllegalStateException("Şəkil deşifrə edilə bilmədi.")
                    val coverBytes = TntPhotoProcessor.createEncryptedShieldCoverJpeg(photo.timestampText)
                    val encryptedJpg = TntEncryptedJpgCodec.encodeEncryptedJpgEnvelope(
                        coverJpegBytes = coverBytes,
                        photo = photo,
                        stampedJpegBytes = plainBytes,
                        password = password,
                    )
                    val fileName = TntPhotoExporter.buildExportFileName(photo, encrypted = true)
                    val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        TntPhotoExporter.saveEncryptedJpgToDownloadsApi29Plus(app, fileName, encryptedJpg)
                    } else {
                        null
                    }
                    Triple(fileName, encryptedJpg, savedPath)
                }
            }
            exportRes.onSuccess { (fileName, bytes, savedPath) ->
                if (savedPath != null) {
                    _vaultState.update {
                        it.copy(
                            isProcessingPhoto = false,
                            vaultMessage = "Şifrəli JPG yükləndi: $savedPath",
                            vaultError = null,
                        )
                    }
                } else {
                    _vaultState.update {
                        it.copy(
                            isProcessingPhoto = false,
                            pendingSafSaveBytes = PendingSafExport(fileName = fileName, bytes = bytes),
                        )
                    }
                }
            }.onFailure { err ->
                _vaultState.update {
                    it.copy(
                        isProcessingPhoto = false,
                        vaultError = err.message ?: "Şifrəli JPG yüklənə bilmədi.",
                    )
                }
            }
        }
    }

    fun completeSafEncryptedSave(uri: Uri?) {
        recordUserActivity()
        isExternalCapturePending = false
        val pending = _vaultState.value.pendingSafSaveBytes ?: return
        _vaultState.update { it.copy(pendingSafSaveBytes = null) }
        if (uri == null) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(pending.bytes)
                    } ?: throw IllegalStateException("Fayl yazıla bilmədi.")
                    true
                }.getOrDefault(false)
            }
            _vaultState.update {
                if (ok) {
                    it.copy(vaultMessage = "Şifrəli JPG seçilmiş qovluğa yükləndi (${pending.fileName}).", vaultError = null)
                } else {
                    it.copy(vaultError = "Şifrəli JPG faylı yazıla bilmədi.")
                }
            }
        }
    }

    suspend fun prepareShareJpgFile(
        photo: TntCapturedPhoto,
        encrypted: Boolean,
        optionalPassword: String?,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val plainBytes = photoVault.loadDecryptedPhotoBytes(photo) ?: return@runCatching null
            val outBytes = if (encrypted) {
                val coverBytes = TntPhotoProcessor.createEncryptedShieldCoverJpeg(photo.timestampText)
                TntEncryptedJpgCodec.encodeEncryptedJpgEnvelope(
                    coverJpegBytes = coverBytes,
                    photo = photo,
                    stampedJpegBytes = plainBytes,
                    password = optionalPassword?.trim()?.takeIf { it.isNotEmpty() },
                )
            } else {
                plainBytes
            }
            val fileName = TntPhotoExporter.buildExportFileName(photo, encrypted = encrypted)
            TntPhotoExporter.writeShareTempJpg(app, fileName, outBytes)
        }.getOrNull()
    }

    private fun readBoundedBytes(stream: InputStream, maxBytes: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) {
                throw IllegalStateException("Fayl ölçüsü icazə verilən həddi (25 MB) aşır")
            }
            baos.write(buf, 0, n)
        }
        return baos.toByteArray()
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
