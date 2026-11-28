package com.aistudio.testbaza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aistudio.testbaza.core.ui.TestBazaRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val databaseId = readStringRes("firestore_database_id")
        val webClientId = readStringRes("default_web_client_id")
        setContent {
            TestBazaRoot(
                firestoreDatabaseId = databaseId,
                defaultWebClientId = webClientId,
            )
        }
    }

    private fun readStringRes(name: String): String = runCatching {
        val id = resources.getIdentifier(name, "string", packageName)
        if (id != 0) getString(id) else ""
    }.getOrDefault("")
}
