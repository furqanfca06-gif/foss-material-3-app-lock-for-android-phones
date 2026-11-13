package com.example.applock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

internal object AppSecurity {
    const val PREFS = "app_lock_settings"
    const val PIN_TAG = "pin_tag"
    const val PIN_LENGTH = "pin_length"
    const val DARK_THEME = "dark_theme"
    const val LOCKED_PACKAGES = "locked_packages"
    const val UNLOCKED_PACKAGE = "unlocked_package"
    const val RECOVERY_Q1 = "recovery_question_1"
    const val RECOVERY_Q2 = "recovery_question_2"
    const val RECOVERY_A1 = "recovery_answer_1"
    const val RECOVERY_A2 = "recovery_answer_2"
    const val KEY_ALIAS = "app_lock_pin_hmac"

    fun pinMatches(pin: String, expected: String): Boolean =
        java.security.MessageDigest.isEqual(pinTag(pin).toByteArray(), expected.toByteArray())

    fun answerTag(slot: Int, answer: String): String = keyedTag("recovery:$slot:${answer.trim().lowercase()}")

    fun answerMatches(slot: Int, answer: String, expected: String): Boolean =
        java.security.MessageDigest.isEqual(answerTag(slot, answer).toByteArray(), expected.toByteArray())

    fun pinTag(pin: String): String = keyedTag(pin)

    private fun keyedTag(value: String): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generator.generateKey()
        }
        return Mac.getInstance("HmacSHA256").run {
            init(key)
            doFinal(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
