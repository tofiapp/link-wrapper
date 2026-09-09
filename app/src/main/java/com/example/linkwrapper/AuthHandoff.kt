package com.example.linkwrapper

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Předání jména a hesla do procesu :authprobe.
 * Údaje jdou šifrovaným souborem v no_backup; po přečtení se smažou.
 */
internal object AuthHandoff {

    private const val FILE = "auth_handoff.bin"

    data class Request(
        val url: String,
        val username: String,
        val password: String
    )

    fun put(context: Context, username: String, password: String, url: String): Boolean {
        return try {
            clear(context)
            val packed = pack(url, username, password)
            val payload = SecretStore.encrypt(packed) ?: packed
            val file = file(context)
            file.outputStream().use { out ->
                out.write(payload)
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun take(context: Context): Request? {
        val file = file(context)
        if (!file.exists()) return null
        val raw = try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
        shred(file)
        if (raw == null || raw.isEmpty()) return null
        val plain = SecretStore.decrypt(raw) ?: raw
        return unpack(plain)
    }

    fun clear(context: Context) {
        shred(file(context))
    }

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE)

    private fun pack(url: String, username: String, password: String): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            out.writeUTF(url)
            out.writeUTF(username)
            out.writeUTF(password)
        }
        return bos.toByteArray()
    }

    private fun unpack(bytes: ByteArray): Request? {
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val url = input.readUTF()
                val user = input.readUTF()
                val pass = input.readUTF()
                if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) null
                else Request(url, user, pass)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun shred(file: File) {
        try {
            if (!file.exists()) return
            RandomAccessFile(file, "rw").use { raf ->
                val n = raf.length().coerceAtMost(1_000_000L).toInt()
                if (n > 0) {
                    raf.seek(0)
                    raf.write(ByteArray(n))
                    raf.fd.sync()
                }
            }
        } catch (_: Exception) {
        }
        try {
            file.delete()
        } catch (_: Exception) {
        }
    }
}
