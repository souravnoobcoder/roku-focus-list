package com.rokufocus

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope

private object AlwaysSaveable : SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
}

/** Drives a [Saver] the way `rememberSaveable` does, so the round trip under test is the real one. */
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> roundTrip(saver: Saver<T, *>, value: T): T {
    val typed = saver as Saver<T, Any>
    val saved = with(typed) { AlwaysSaveable.save(value) }
    checkNotNull(saved) { "saver refused to save $value" }
    return checkNotNull(typed.restore(saved)) { "saver refused to restore $saved" }
}
