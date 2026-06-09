package dev.poddispatcher

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("pod_dispatcher", Context.MODE_PRIVATE)

    /** Schema id of the user's preferred target app, e.g. "pocket-casts". */
    var preferredTargetId: String?
        get() = prefs.getString(KEY_PREFERRED_TARGET, null)
        set(value) {
            prefs.edit().putString(KEY_PREFERRED_TARGET, value).apply()
        }

    private companion object {
        const val KEY_PREFERRED_TARGET = "preferred_target"
    }
}
