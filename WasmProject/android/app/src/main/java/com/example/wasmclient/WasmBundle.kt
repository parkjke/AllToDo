package com.example.wasmclient

data class WasmBundle(
    val version: String,
    val ciphertext_b64: String,
    val iv_b64: String,
    val tag_b64: String
)
