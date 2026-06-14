package com.example.tv_controller

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest

object SecurityVault {

    fun getPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "tv_secret_shared_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Double hashes the PIN string: sha256(sha256(PIN))
    fun computeMasterHash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val firstPass = digest.digest(pin.toByteArray())
        val secondPass = digest.digest(firstPass)
        return secondPass.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}