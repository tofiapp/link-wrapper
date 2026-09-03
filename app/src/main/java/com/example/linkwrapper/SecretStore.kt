package com.example.linkwrapper

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Šifrování údajů klíčem v Android Keystore.
 *
 * Klíč neopustí hardware / TEE tabletu. Na disku je jen ciphertext.
 * Bez androidx.security — ten na některých tabletech padá.
 */
internal object SecretStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "psst_session_aes"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    fun encryptToString(plain: String): String? {
        val bytes = encrypt(plain.toByteArray(Charsets.UTF_8)) ?: return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decryptFromString(blob: String): String? {
        return try {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            val plain = decrypt(raw) ?: return null
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun encrypt(plain: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv ?: return null
            val ciphertext = cipher.doFinal(plain)
            ByteArray(iv.size + ciphertext.size).also { out ->
                System.arraycopy(iv, 0, out, 0, iv.size)
                System.arraycopy(ciphertext, 0, out, iv.size, ciphertext.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decrypt(blob: ByteArray): ByteArray? {
        if (blob.size <= GCM_IV_BYTES) return null
        return try {
            val iv = blob.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteKey() {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }
}
