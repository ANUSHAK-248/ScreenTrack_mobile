package com.example.tv_controller

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest

object SecurityVault {

    private const val TAG = "SecurityVault_Server"

    fun getPrefs(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "tv_secret_shared_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Master key initialization or decryption failed. Wiping corrupted prefs.", e)

            // Wipe the corrupted pref file out of sandbox memory cleanly
            context.getSharedPreferences("tv_secret_shared_prefs", Context.MODE_PRIVATE).edit().clear().apply()

            // Re-attempt clean creation
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "tv_secret_shared_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun computeMasterHash(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val current = pin.toByteArray()

        // Pass 1: String Conversion Matching Laptop/Client Engine Layout Specs
        val firstHash = md.digest(current).joinToString("") { "%02x".format(it) }
        // Pass 2
        val masterHash = md.digest(firstHash.toByteArray()).joinToString("") { "%02x".format(it) }

        return masterHash
    }
}