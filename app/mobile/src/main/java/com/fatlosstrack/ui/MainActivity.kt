package com.fatlosstrack.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.login.LoginScreen
import com.fatlosstrack.ui.theme.FatLossTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var openAiService: OpenAiService

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FatLossTrackTheme {
                val authState by authManager.authState.collectAsState()

                when (authState) {
                    is AuthManager.AuthState.Loading -> {
                        // Could show a splash — for now just blank
                    }
                    is AuthManager.AuthState.SignedOut,
                    is AuthManager.AuthState.Error -> {
                        LoginScreen(
                            authManager = authManager,
                            onSignedIn = { /* state flow will trigger recomposition */ },
                        )
                    }
                    is AuthManager.AuthState.SignedIn -> {
                        FatLossTrackNavGraph(
                            authManager = authManager,
                            openAiService = openAiService,
                            preferencesManager = preferencesManager,
                        )
                    }
                }
            }
        }
    }
}
