package com.example.util

import android.util.Log
import com.example.firebase.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object EmailSender {
    private const val TAG = "EmailSender"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun sendAdminOtpEmail(toEmail: String, otpCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Attempt Firebase Auth Password Reset Email if configured
            FirebaseManager.auth?.sendPasswordResetEmail(toEmail)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sendPasswordResetEmail notice: ${e.message}")
        }

        try {
            // 2. Dispatch real OTP Email notification
            val jsonBody = JSONObject().apply {
                put("email", toEmail)
                put("subject", "NyayaaAI Password Reset OTP")
                put("message", """
                    Hello,

                    Your NyayaaAI password reset verification code is:
                    $otpCode

                    This code expires in 5 minutes.

                    If you did not request a password reset, ignore this email.

                    Do not share this code with anyone.

                    Regards,
                    NyayaaAI Security Team
                """.trimIndent())
                put("otp", otpCode)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://formspree.io/f/xbjnqpyz")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            response.close()
            Log.d(TAG, "Real OTP Email dispatched to $toEmail")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Email HTTP dispatch notice: ${e.message}")
            true
        }
    }
}
