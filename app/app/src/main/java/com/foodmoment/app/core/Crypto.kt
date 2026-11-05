package com.foodmoment.app.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key / 口令的本地加密（SET-03）。
 *
 * 用 Android Keystore 里的 AES-256-GCM 密钥加密，`iv||ciphertext` 以 Base64 存储。
 * 不依赖已停止演进的 androidx.security-crypto。
 */
object Crypto {

    private const val ALIAS = "foodmoment_key_v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TAG_BITS = 128
    private const val PLAIN_PREFIX = "plain:"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(1 + iv.size + body.size)
        packed[0] = iv.size.toByte()
        System.arraycopy(iv, 0, packed, 1, iv.size)
        System.arraycopy(body, 0, packed, 1 + iv.size, body.size)
        Base64.encodeToString(packed, Base64.NO_WRAP)
    } catch (t: Throwable) {
        // Keystore 异常时不阻塞用户使用：退化为明确标记的明文，读取端可识别。
        PLAIN_PREFIX + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        if (stored.startsWith(PLAIN_PREFIX)) {
            return runCatching {
                String(Base64.decode(stored.removePrefix(PLAIN_PREFIX), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrNull()
        }
        return runCatching {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            val ivSize = packed[0].toInt()
            val iv = packed.copyOfRange(1, 1 + ivSize)
            val body = packed.copyOfRange(1 + ivSize, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }
}
