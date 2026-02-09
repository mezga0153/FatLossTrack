package com.fatlosstrack.ui.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatlosstrack.R
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Login / onboarding screen — shown when user is not authenticated.
 * Single "Sign in with Google" button, minimal and on-brand.
 */
@Composable
fun LoginScreen(
    authManager: AuthManager,
    onSignedIn: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val authState by authManager.authState.collectAsState()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val signInFailedMsg = stringResource(R.string.login_sign_in_failed)

    // Legacy GoogleSignIn intent launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            val res = authManager.handleSignInResult(result.data)
            isLoading = false
            if (res.isFailure) {
                errorMessage = res.exceptionOrNull()?.message ?: signInFailedMsg
            }
        }
    }

    // Navigate when signed in
    LaunchedEffect(authState) {
        if (authState is AuthManager.AuthState.SignedIn) {
            onSignedIn()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Surface,
                        PrimaryContainer,
                        Surface,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(48.dp))

            // App icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(44.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Title
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                ),
                color = OnSurface,
            )

            // Tagline
            Text(
                text = stringResource(R.string.login_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            Spacer(Modifier.height(32.dp))

            // Feature highlights
            FeatureRow(stringResource(R.string.login_feature_1))
            FeatureRow(stringResource(R.string.login_feature_2))
            FeatureRow(stringResource(R.string.login_feature_3))

            Spacer(Modifier.height(40.dp))

            // Google Sign-In button
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    signInLauncher.launch(authManager.getSignInIntent())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.DarkGray,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                // Google "G" logo approximation with text
                Text(
                    text = "G",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF4285F4),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isLoading) stringResource(R.string.login_signing_in) else stringResource(R.string.login_continue_google),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.DarkGray,
                )
            }

            // Error message
            if (errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = Tertiary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Privacy note
            Text(
                text = stringResource(R.string.login_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Primary),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurface,
        )
    }
}
