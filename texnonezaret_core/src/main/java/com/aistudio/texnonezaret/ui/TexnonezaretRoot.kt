package com.aistudio.texnonezaret.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.texnonezaret.ui.theme.TexnonezaretTheme

@Composable
fun TexnonezaretRoot(
    viewModel: TexnonezaretViewModel = viewModel(),
) {
    val pinState by viewModel.pinState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val securityState by viewModel.securityState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkIdleLockOnForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TexnonezaretTheme(themeMode = searchState.themeMode) {
        if (securityState.tamperAlertActive) {
            TamperLockScreen(
                reasons = securityState.integrityReport?.tamperReasons.orEmpty(),
                errorMessage = securityState.masterPasswordError,
                cooldownSecondsLeft = securityState.masterCooldownSecondsLeft,
                onUnlockWithPassword = { pw -> viewModel.clearTamperLockWithPassword(pw) },
            )
        } else if (pinState.isLocked) {
            PinLockScreen(
                state = pinState,
                masterErrorMessage = securityState.masterPasswordError,
                masterCooldownSecondsLeft = securityState.masterCooldownSecondsLeft,
                onDigitPress = viewModel::pressPinDigit,
                onBackspacePress = viewModel::pressPinBackspace,
                onClearPress = viewModel::pressPinClear,
                onBackToSetup = { viewModel.openPinScreen(PinFlowMode.SETUP) },
                onCancelChange = viewModel::cancelPinChange,
                onConfirmResetWithPassword = viewModel::resetPinWithInterventionPassword,
            )
        } else {
            MainSearchScreen(
                state = searchState,
                securityState = securityState,
                effects = viewModel.effects,
                onImportSpreadsheet = viewModel::importSpreadsheetUri,
                onClearData = viewModel::clearLoadedSpreadsheet,
                onRequestProtectedAction = viewModel::requestProtectedAction,
                onLockInterventionMode = viewModel::lockInterventionMode,
                onOpenSecurityCenter = viewModel::openSecurityCenter,
                onToggleColumnMapExpanded = viewModel::toggleColumnMapExpanded,
                onPlateQueryChange = viewModel::updatePlateQuery,
                onNameQueryChange = viewModel::updateNameQuery,
                onSelectStatusFilter = viewModel::selectStatusFilter,
                onCycleThemeMode = viewModel::cycleThemeMode,
                onLockApp = viewModel::lockNow,
                onBuildBackupJson = viewModel::buildBackupJson,
                onImportBackupUri = viewModel::importBackupUri,
            )

            if (securityState.showInterventionDialog) {
                InterventionPasswordDialog(
                    actionLabel = securityState.pendingActionLabel,
                    errorMessage = securityState.masterPasswordError,
                    cooldownSecondsLeft = securityState.masterCooldownSecondsLeft,
                    onSubmit = viewModel::submitInterventionPassword,
                    onDismiss = viewModel::dismissInterventionDialog,
                )
            }

            if (securityState.showSecurityCenterDialog) {
                SecurityCenterDialog(
                    report = securityState.integrityReport,
                    isInterventionUnlocked = securityState.isInterventionUnlocked,
                    onUnlockIntervention = {
                        viewModel.requestProtectedAction(PendingInterventionAction.OpenSecurityCenter)
                    },
                    onLockIntervention = viewModel::lockInterventionMode,
                    onDismiss = viewModel::dismissSecurityCenter,
                )
            }
        }
    }
}
