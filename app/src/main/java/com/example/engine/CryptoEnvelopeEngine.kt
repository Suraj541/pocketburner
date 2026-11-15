package com.example.engine

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class EncryptedEnvelope(
    val wirePayload: String,
    val ivHex: String,
    val keyFingerprint: String,
    val cipherBytesCount: Int,
)

object CryptoEnvelopeEngine {
    private const val FRAME_PREFIX = "PBENC1:"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 10_000
    private val secureRandom = SecureRandom()

    fun keyFingerprint(passphrase: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(passphrase.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString(":") { "%02X".format(it) }
    }

    fun encryptCommand(plaintext: ByteArray, passphrase: String): EncryptedEnvelope {
        val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
        val key = deriveAes256Key(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD("PocketBurner-v1".toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)

        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val wire = "$FRAME_PREFIX$saltB64:$ivB64:$cipherB64\n"

        return EncryptedEnvelope(
            wirePayload = wire,
            ivHex = iv.joinToString("") { "%02x".format(it) },
            keyFingerprint = keyFingerprint(passphrase),
            cipherBytesCount = ciphertext.size,
        )
    }

    fun decryptEnvelope(frame: String, passphrase: String): ByteArray? {
        val trimmed = frame.trim()
        if (!trimmed.startsWith(FRAME_PREFIX)) return null
        val parts = trimmed.removePrefix(FRAME_PREFIX).split(":")
        if (parts.size != 3) return null
        return try {
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            val key = deriveAes256Key(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD("PocketBurner-v1".toByteArray(Charsets.UTF_8))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    fun createTlsSocketFactory(allowSelfSignedLabCerts: Boolean): SSLSocketFactory {
        if (!allowSelfSignedLabCerts) {
            return SSLSocketFactory.getDefault() as SSLSocketFactory
        }
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, secureRandom)
        return ctx.socketFactory
    }

    private fun deriveAes256Key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val effectivePass = passphrase.ifBlank { "pocketburner-default" }.toCharArray()
        val spec = PBEKeySpec(effectivePass, salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
