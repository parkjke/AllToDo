package com.example.alltodo.wasm

import android.content.Context

class WasmStorage(context: Context) {
    private val prefs = context.getSharedPreferences("wasm_prefs", Context.MODE_PRIVATE)

    fun save(version: String, blobJson: String) {
        prefs.edit()
            .putString("version", version)
            .putString("blob", blobJson)
            .apply()
    }

    fun load(): Pair<String, String>? {
        val version = prefs.getString("version", null)
        val blob = prefs.getString("blob", null)
        return if (version != null && blob != null) {
            version to blob
        } else {
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
