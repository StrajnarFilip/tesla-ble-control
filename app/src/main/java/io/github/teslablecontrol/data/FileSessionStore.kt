package io.github.teslablecontrol.data

import android.util.Base64
import io.github.teslablecontrol.core.SessionStore
import io.github.teslablecontrol.protocol.Domain
import io.github.teslablecontrol.protocol.dispatch.SessionCacheEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Session caches, one small JSON file per car in a directory that Android never backs up. Session
 * info isn't secret (it holds counters and the car's public key), but it is useless elsewhere.
 */
class FileSessionStore(private val directory: File) : SessionStore {
    @Serializable
    private data class Entry(val domain: Int, val sessionInfo: String, val createdAtMillis: Long)

    private val json = Json { ignoreUnknownKeys = true }

    private fun fileFor(vin: String) = File(directory, "${vin.filter { it.isLetterOrDigit() }}.json")

    @Synchronized
    override fun load(vin: String): List<SessionCacheEntry> {
        val file = fileFor(vin)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<Entry>>(file.readText()).mapNotNull { entry ->
                Domain.fromNumber(entry.domain)?.let {
                    SessionCacheEntry(it, Base64.decode(entry.sessionInfo, Base64.NO_WRAP), entry.createdAtMillis)
                }
            }
        }.getOrElse {
            file.delete()
            emptyList()
        }
    }

    @Synchronized
    override fun save(vin: String, entries: List<SessionCacheEntry>) {
        directory.mkdirs()
        val encoded = json.encodeToString(
            entries.map { Entry(it.domain.number, Base64.encodeToString(it.sessionInfo, Base64.NO_WRAP), it.createdAtMillis) },
        )
        // Write-then-rename so a crash never leaves a half-written cache behind.
        val temp = File(directory, "${fileFor(vin).name}.tmp")
        temp.writeText(encoded)
        if (!temp.renameTo(fileFor(vin))) {
            fileFor(vin).writeText(encoded)
            temp.delete()
        }
    }

    @Synchronized
    override fun clear(vin: String) {
        fileFor(vin).delete()
    }
}
