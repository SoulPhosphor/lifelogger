package com.datadragon.app.data

import java.util.UUID

/**
 * The only two ordinary UUID paths for persisted user objects.
 *
 * New objects receive a freshly generated identity. Objects reconstructed from
 * stored or transferred data must supply their established identity; that path
 * never falls back to generating one.
 */
object StableUuid {
    fun createNew(): String = UUID.randomUUID().toString()

    fun restoreExisting(uuid: String): String {
        require(uuid.isNotBlank()) { "An existing object must have a non-blank UUID" }
        return uuid
    }
}
