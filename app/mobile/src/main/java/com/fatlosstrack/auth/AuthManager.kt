package com.fatlosstrack.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Sign-In via legacy GoogleSignIn API + Firebase Auth.
 *
 * Flow:
 * 1. Launch GoogleSignInClient intent (account chooser)
 * 2. Activity result returns a Google ID token
 * 3. Exchange token with Firebase Auth for a FirebaseUser
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val firebaseAuth = FirebaseAuth.getInstance()

    companion object {
        const val WEB_CLIENT_ID = "65050648627-kg563lumprpi30hh9kblc6iled4ada1t.apps.googleusercontent.com"
    }

    sealed class AuthState {
        data object Loading : AuthState()
        data object SignedOut : AuthState()
        data class SignedIn(val user: FirebaseUser) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentUser: FirebaseUser? get() = firebaseAuth.currentUser

    init {
        val user = firebaseAuth.currentUser
        _authState.value = if (user != null) AuthState.SignedIn(user) else AuthState.SignedOut
    }

    /** Build the sign-in intent to launch via ActivityResultLauncher. */
    fun getSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    /** Handle the result from the GoogleSignIn intent. Call from ActivityResult callback. */
    suspend fun handleSignInResult(data: Intent?): Result<FirebaseUser> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account.idToken ?: throw Exception("No ID token from Google")

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: throw Exception("Firebase auth returned null user")

            _authState.value = AuthState.SignedIn(user)
            Result.success(user)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Sign-in failed")
            Result.failure(e)
        }
    }

    /** Sign out from both Firebase and Google. */
    fun signOut() {
        firebaseAuth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso).signOut()
        _authState.value = AuthState.SignedOut
    }
}
