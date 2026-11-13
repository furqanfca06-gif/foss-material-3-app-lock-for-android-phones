package com.example.applock

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LockActivity : FragmentActivity() {
    private val targetPackage: String by lazy { intent.getStringExtra(EXTRA_PACKAGE).orEmpty() }
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString()
        }.getOrDefault("Protected app")
        val prefs = getSharedPreferences(AppSecurity.PREFS, MODE_PRIVATE)

        setContent {
            var error by remember { mutableStateOf("") }
            var appIcon by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            LaunchedEffect(targetPackage) { appIcon = withContext(Dispatchers.IO) { loadAppIcon(this@LockActivity, targetPackage) } }
            var mode by remember { mutableStateOf("lock") }
            var pinLength by remember { mutableIntStateOf(prefs.getInt(AppSecurity.PIN_LENGTH, 6)) }
            var firstPin by remember { mutableStateOf("") }
            var answer1 by remember { mutableStateOf("") }
            var answer2 by remember { mutableStateOf("") }
            var question1 by remember { mutableStateOf("") }
            var question2 by remember { mutableStateOf("") }
            AppLockTheme(prefs.getBoolean(AppSecurity.DARK_THEME, true)) {
                Surface(Modifier.fillMaxSize(), color = LockColors.Canvas) {
                    BackHandler(enabled = true) { if (mode == "recovery") mode = "lock" }
                    if (mode == "enroll") {
                        RecoveryQuestions(question1, { question1 = it }, answer1, { answer1 = it }, question2, { question2 = it }, answer2, { answer2 = it }, error) {
                            if (question1.isBlank() || answer1.isBlank() || question2.isBlank() || answer2.isBlank() || question1.trim().equals(question2.trim(), true)) error = "Enter two different questions and both answers."
                            else {
                                prefs.edit().putString(AppSecurity.RECOVERY_Q1, question1.trim()).putString(AppSecurity.RECOVERY_Q2, question2.trim())
                                    .putString(AppSecurity.RECOVERY_A1, AppSecurity.answerTag(1, answer1)).putString(AppSecurity.RECOVERY_A2, AppSecurity.answerTag(2, answer2)).commit()
                                unlockAndFinish()
                            }
                        }
                    } else if (mode == "recovery") {
                        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
                            Text("Verify recovery answers", color = LockColors.OnSurface, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(16.dp))
                            Text(prefs.getString(AppSecurity.RECOVERY_Q1, "") ?: "", color = LockColors.Muted)
                            OutlinedTextField(answer1, { answer1 = it }, Modifier.fillMaxWidth(), label = { Text("Answer 1") }, singleLine = true)
                            Spacer(Modifier.height(12.dp))
                            Text(prefs.getString(AppSecurity.RECOVERY_Q2, "") ?: "", color = LockColors.Muted)
                            OutlinedTextField(answer2, { answer2 = it }, Modifier.fillMaxWidth(), label = { Text("Answer 2") }, singleLine = true)
                            Text(error, color = LockColors.Error, modifier = Modifier.padding(top = 8.dp))
                            Button(onClick = {
                                val lockedUntil = prefs.getLong("pin_lockout_until", 0L)
                                if (System.currentTimeMillis() < lockedUntil) {
                                    error = "Too many attempts. Wait 30 seconds and try again."
                                } else {
                                    val valid = runCatching { AppSecurity.answerMatches(1, answer1, prefs.getString(AppSecurity.RECOVERY_A1, "")!!) && AppSecurity.answerMatches(2, answer2, prefs.getString(AppSecurity.RECOVERY_A2, "")!!) }.getOrDefault(false)
                                    if (valid) {
                                        prefs.edit().remove("pin_failures").remove("pin_lockout_until").apply()
                                        mode = "reset"; error = ""
                                    } else {
                                        val failures = prefs.getInt("pin_failures", 0) + 1
                                        val editor = prefs.edit().putInt("pin_failures", failures)
                                        if (failures >= 5) editor.putLong("pin_lockout_until", System.currentTimeMillis() + 30_000L).putInt("pin_failures", 0)
                                        editor.apply(); error = "Answers did not match. Try again."
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = CircleShape) { Text("Verify answers") }
                            TextButton(onClick = { mode = "lock" }) { Text("Back to PIN") }
                        }
                    } else if (mode == "reset" || mode == "confirm") {
                        PinPad(title = if (mode == "reset") "Create a new PIN" else "Confirm new PIN", subtitle = "Choose a $pinLength-digit PIN", error = error, pinLength = pinLength,
                            onPinLengthChange = if (mode == "reset") ({ pinLength = it; error = "" }) else null,
                            onDigit = { pin -> if (mode == "reset") { firstPin = pin; mode = "confirm" } else if (pin == firstPin) {
                                prefs.edit().putString(AppSecurity.PIN_TAG, AppSecurity.pinTag(pin)).putInt(AppSecurity.PIN_LENGTH, pinLength).commit(); unlockAndFinish()
                            } else { mode = "reset"; error = "PINs did not match. Start again." } })
                    } else {
                    PinPad(
                        title = appLabel,
                        subtitle = "LOCKED BY APP LOCK · ENTER ${prefs.getInt(AppSecurity.PIN_LENGTH, 6)}-DIGIT PIN",
                        error = error,
                        biometricAction = ::showBiometric,
                        appIcon = appIcon,
                        pinLength = prefs.getInt(AppSecurity.PIN_LENGTH, 6),
                        recoveryAction = if (prefs.contains(AppSecurity.RECOVERY_Q1)) ({ mode = "recovery"; error = "" }) else null,
                        onDigit = { pin ->
                            val enroll = intent.getBooleanExtra(EXTRA_ENROLL_RECOVERY, false)
                            if (!checkPin(pin, deferUnlock = enroll)) {
                                error = if (System.currentTimeMillis() < getSharedPreferences(AppSecurity.PREFS, MODE_PRIVATE).getLong("pin_lockout_until", 0L)) {
                                    "Too many attempts. Wait 30 seconds and try again."
                                } else "Incorrect PIN. Try again."
                            } else if (enroll) { mode = "enroll"; error = "" }
                        }
                    )
                    }
                }
            }
        }
    }

    private fun checkPin(pin: String, deferUnlock: Boolean = false): Boolean {
        val prefs = getSharedPreferences(AppSecurity.PREFS, MODE_PRIVATE)
        val expected = prefs.getString(AppSecurity.PIN_TAG, null) ?: return false
        if (System.currentTimeMillis() < prefs.getLong("pin_lockout_until", 0L)) return false
        val matches = runCatching { AppSecurity.pinMatches(pin, expected) }.getOrDefault(false)
        if (matches) {
            prefs.edit().remove("pin_failures").remove("pin_lockout_until").apply()
            if (!deferUnlock) unlockAndFinish()
        } else {
            val failures = prefs.getInt("pin_failures", 0) + 1
            val editor = prefs.edit().putInt("pin_failures", failures)
            if (failures >= 5) editor.putLong("pin_lockout_until", System.currentTimeMillis() + 30_000L).putInt("pin_failures", 0)
            editor.apply()
        }
        return matches
    }

    private fun showBiometric() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) return
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = unlockAndFinish()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock ${appName()}")
                .setSubtitle("Verify with your device biometric")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun appName(): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString()
    }.getOrDefault("protected app")

    private fun unlockAndFinish() {
        if (completed) return
        completed = true
        getSharedPreferences(AppSecurity.PREFS, MODE_PRIVATE).edit().putString(AppSecurity.UNLOCKED_PACKAGE, targetPackage).apply()
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "protected_package"
        const val EXTRA_ENROLL_RECOVERY = "enroll_recovery"
    }
}
