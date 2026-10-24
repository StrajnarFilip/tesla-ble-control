package io.github.teslablecontrol.keys

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.teslablecontrol.protocol.crypto.KeyCodec
import io.github.teslablecontrol.protocol.crypto.SoftwareEcdhKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * This phone's car key: a P-256 private key, stored encrypted by a non-exportable Android Keystore
 * AES-256-GCM key. The file lives in noBackupFilesDir because the Keystore key can't move to another
 * phone, so a restored copy could never be decrypted.
 */
class KeyManager(context: Context) {
    enum class Status {
        /** No key yet; one is created when pairing starts. */
        NONE,
        READY,
        /** A key file exists but can't be decrypted (for example after the Keystore was reset). */
        UNUSABLE,
    }

    private val file = File(context.noBackupFilesDir, "vehicle_key.bin")
    private val lock = Any()
    private var cached: SoftwareEcdhKey? = null

    private val _status = MutableStateFlow(Status.NONE)
    val status: StateFlow<Status> = _status.asStateFlow()

    init {
        synchronized(lock) { load() }
    }

    /** The key, or null if there is none or it can't be read. */
    fun currentKey(): SoftwareEcdhKey? = synchronized(lock) { cached ?: load() }

    /** The key, creating one if none exists. Fails if an existing key is unusable (see [replaceWithNewKey]). */
    fun getOrCreateKey(): SoftwareEcdhKey = synchronized(lock) {
        currentKey()?.let { return it }
        check(_status.value != Status.UNUSABLE) { "the stored key can't be decrypted" }
        store(SoftwareEcdhKey.generate())
    }

    /** Discards any existing key and creates a new one. The new key must be paired again. */
    fun replaceWithNewKey(): SoftwareEcdhKey = synchronized(lock) {
        file.delete()
        cached = null
        store(SoftwareEcdhKey.generate())
    }

    /** Replaces the key with an imported PEM key (for example one created with tesla-control). */
    fun importPem(pem: String): SoftwareEcdhKey = synchronized(lock) {
        val key = KeyCodec.parsePrivateKeyPem(pem)
        file.delete()
        cached = null
        store(key)
    }

    fun publicKeyPem(): String? = currentKey()?.let { KeyCodec.publicKeyToPem(it.publicKey) }

    /** Exports the private key so it can be backed up or used with Tesla's command-line tools. */
    fun exportPrivatePem(): String? = currentKey()?.let { KeyCodec.privateKeyToPem(it) }

    private fun store(key: SoftwareEcdhKey): SoftwareEcdhKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val sealed = cipher.doFinal(key.privateScalar())
        val iv = cipher.iv
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + sealed)
        if (!temp.renameTo(file)) {
            file.writeBytes(temp.readBytes())
            temp.delete()
        }
        cached = key
        _status.value = Status.READY
        return key
    }

    private fun load(): SoftwareEcdhKey? {
        if (!file.exists()) {
            _status.value = Status.NONE
            return null
        }
        return try {
            val blob = file.readBytes()
            require(blob.size > 2 && blob[0] == FORMAT_VERSION) { "unknown key file format" }
            val ivSize = blob[1].toInt()
            val iv = blob.copyOfRange(2, 2 + ivSize)
            val sealed = blob.copyOfRange(2 + ivSize, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingWrappingKey() ?: error("wrapping key missing"), GCMParameterSpec(128, iv))
            SoftwareEcdhKey.fromPrivateScalar(cipher.doFinal(sealed)).also {
                cached = it
                _status.value = Status.READY
            }
        } catch (e: Exception) {
            cached = null
            _status.value = Status.UNUSABLE
            null
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingWrappingKey(): SecretKey? = keyStore().getKey(ALIAS, null) as? SecretKey

    private fun wrappingKey(): SecretKey = existingWrappingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }
        .generateKey()

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "tesla_ble_key_wrap"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
    }
}
