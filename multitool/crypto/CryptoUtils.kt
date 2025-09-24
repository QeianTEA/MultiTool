package com.example.multitool.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoUtils {
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 12   // 96-bit nonce for GCM
    private const val TAG_SIZE = 128 // 128-bit auth tag

    // TODO: Replace this with a securely exchanged key or derive from passphrase
    private val sharedKey: SecretKey by lazy {
        val kgen = KeyGenerator.getInstance("AES")
        kgen.init(KEY_SIZE)
        kgen.generateKey()
    }

    /** Encrypt plainText -> Base64(IV || ciphertext) */
    fun encrypt(plainText: ByteArray): String {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, GCMParameterSpec(TAG_SIZE, iv))
        val cipherText = cipher.doFinal(plainText)
        // Prepend IV
        val combined = iv + cipherText
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Decrypt Base64(IV || ciphertext) -> plainText */
    fun decrypt(data: String): ByteArray? {
        try {
            val combined = Base64.decode(data, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val cipherText = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, sharedKey, GCMParameterSpec(TAG_SIZE, iv))
            return cipher.doFinal(cipherText)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
