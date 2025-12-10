package com.example.wasmclient

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object WasmCrypto {
    private const val AES_KEY = "0123456789abcdef0123456789abcdef"
    private val keySpec = SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES")

    @Throws(Exception::class)
    fun decrypt(bundle: WasmBundle): ByteArray {
        val iv = Base64.decode(bundle.iv_b64, Base64.DEFAULT)
        val ciphertext = Base64.decode(bundle.ciphertext_b64, Base64.DEFAULT)
        val tag = Base64.decode(bundle.tag_b64, Base64.DEFAULT)

        val combined = ByteArray(ciphertext.size + tag.size)
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.size)
        System.arraycopy(tag, 0, combined, ciphertext.size, tag.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)

        return cipher.doFinal(combined)
    }
}
