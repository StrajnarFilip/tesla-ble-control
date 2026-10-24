package io.github.teslablecontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Which controls make sense for the car. */
@Serializable
enum class CarModel(val label: String) {
    MODEL_3("Model 3"),
    MODEL_Y("Model Y"),
    MODEL_S("Model S"),
    MODEL_X("Model X"),
    CYBERTRUCK("Cybertruck"),
    OTHER("Other"),
}

@Serializable
data class VehicleProfile(
    val vin: String,
    val name: String,
    val model: CarModel = CarModel.MODEL_3,
    /** Role requested when pairing ("owner" or "driver"). */
    val role: String = "owner",
    /** Bluetooth address learned from the first scan, so later connections skip scanning. */
    val bleAddress: String? = null,
    val paired: Boolean = false,
    val pairedAtMillis: Long? = null,
    /**
     * Settings the car doesn't report (low power mode, accessory power, guest mode), as this phone
     * last set them, by command family.
     */
    val lastSet: Map<String, Boolean> = emptyMap(),
)

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "vehicles")

class ProfileStore(context: Context, scope: CoroutineScope) {
    private val store = context.profileDataStore
    private val key = stringPreferencesKey("profiles")
    private val json = Json { ignoreUnknownKeys = true }

    /** Null until loaded. */
    val profiles: StateFlow<List<VehicleProfile>?> = store.data
        .map { prefs -> prefs[key]?.let { decode(it) } ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun decode(text: String): List<VehicleProfile> =
        runCatching { json.decodeFromString<List<VehicleProfile>>(text) }.getOrDefault(emptyList())

    fun find(vin: String): VehicleProfile? = profiles.value?.firstOrNull { it.vin == vin }

    // Each change returns the list as saved; [profiles] catches up a moment later.
    suspend fun upsert(profile: VehicleProfile) = edit { list -> list.filterNot { it.vin == profile.vin } + profile }

    suspend fun update(vin: String, transform: (VehicleProfile) -> VehicleProfile) =
        edit { list -> list.map { if (it.vin == vin) transform(it) else it } }

    suspend fun remove(vin: String) = edit { list -> list.filterNot { it.vin == vin } }

    private suspend fun edit(transform: (List<VehicleProfile>) -> List<VehicleProfile>): List<VehicleProfile> {
        var saved = emptyList<VehicleProfile>()
        store.edit { prefs ->
            val current = prefs[key]?.let { decode(it) } ?: emptyList()
            saved = transform(current)
            prefs[key] = json.encodeToString(saved)
        }
        return saved
    }
}
