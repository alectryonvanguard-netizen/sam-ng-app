package com.example.twirlly

import android.content.Context

/**
 * Sign-in hook. The page is ready; the default provider keeps everything on this phone.
 * To connect your own account system later, create an AuthProvider and set `Auth.provider = YourProvider`.
 * Each function returns null on success, or a message to show the user.
 */
interface AuthProvider {
    suspend fun signIn(email: String, password: String): String?
    suspend fun signUp(email: String, password: String): String?
    suspend fun google(context: Context): String?
}

/** Default: checks the form only. The password is never stored. */
object LocalAuth : AuthProvider {
    private fun check(email: String, password: String): String? = when {
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
        password.length < 6 -> "Password must have at least 6 characters."
        else -> null
    }

    override suspend fun signIn(email: String, password: String): String? = check(email, password)
    override suspend fun signUp(email: String, password: String): String? = check(email, password)
    override suspend fun google(context: Context): String? = "Google sign-in is not connected yet."
}

object Auth {
    var provider: AuthProvider = LocalAuth
}
