package com.aistudio.texnonezaret

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.texnonezaret.ui.MainSearchScreen
import com.aistudio.texnonezaret.ui.PinFlowMode
import com.aistudio.texnonezaret.ui.PinLockScreen
import com.aistudio.texnonezaret.ui.TexnonezaretViewModel
import com.aistudio.texnonezaret.ui.theme.TexnonezaretTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TexnonezaretViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val pinState by viewModel.pinState.collectAsStateWithLifecycle()
            val searchState by viewModel.searchState.collectAsStateWithLifecycle()
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
                if (pinState.isLocked) {
                    PinLockScreen(
                        state = pinState,
                        onDigitPress = viewModel::pressPinDigit,
                        onBackspacePress = viewModel::pressPinBackspace,
                        onClearPress = viewModel::pressPinClear,
                        onBackToSetup = { viewModel.openPinScreen(PinFlowMode.SETUP) },
                        onCancelChange = viewModel::cancelPinChange,
                        onConfirmResetData = viewModel::resetPinAndWipeData,
                    )
                } else {
                    MainSearchScreen(
                        state = searchState,
                        onImportSpreadsheet = viewModel::importSpreadsheetUri,
                        onClearSpreadsheet = viewModel::clearLoadedSpreadsheet,
                        onUpdateColumnMapping = viewModel::updateColumnMapping,
                        onToggleColumnMapExpanded = viewModel::toggleColumnMapExpanded,
                        onPlateQueryChange = viewModel::updatePlateQuery,
                        onNameQueryChange = viewModel::updateNameQuery,
                        onSelectStatusFilter = viewModel::selectStatusFilter,
                        onCycleThemeMode = viewModel::cycleThemeMode,
                        onRequestChangePin = { viewModel.openPinScreen(PinFlowMode.CHANGE_OLD) },
                        onLockApp = viewModel::lockNow,
                        onBuildBackupJson = viewModel::buildBackupJson,
                        onImportBackupUri = viewModel::importBackupUri,
                    )
                }
            }
        }
    }
}
