package com.example.applock

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { getSharedPreferences(AppSecurity.PREFS, MODE_PRIVATE) }
            var hasPin by remember { mutableStateOf(prefs.contains(AppSecurity.PIN_TAG)) }
            var darkTheme by remember { mutableStateOf(prefs.getBoolean(AppSecurity.DARK_THEME, true)) }
            SideEffect {
                window.statusBarColor = if (darkTheme) android.graphics.Color.rgb(19, 19, 21) else android.graphics.Color.rgb(251, 248, 254)
                window.navigationBarColor = window.statusBarColor
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !darkTheme
            }
            AppLockTheme(darkTheme) {
                Surface(Modifier.fillMaxSize(), color = LockColors.Canvas) {
                    if (hasPin) {
                        Dashboard(
                            isServiceEnabled = ::serviceEnabled,
                            openAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            openRecoverySetup = { startActivity(Intent(this, LockActivity::class.java).putExtra(LockActivity.EXTRA_PACKAGE, packageName).putExtra(LockActivity.EXTRA_ENROLL_RECOVERY, true)) },
                            darkTheme = darkTheme,
                            onDarkThemeChange = {
                                darkTheme = it
                                prefs.edit().putBoolean(AppSecurity.DARK_THEME, it).apply()
                            }
                        )
                    } else {
                        PinSetup { pin, pinLength, q1, a1, q2, a2 ->
                            runCatching {
                                prefs.edit()
                                    .putString(AppSecurity.PIN_TAG, AppSecurity.pinTag(pin))
                                    .putInt(AppSecurity.PIN_LENGTH, pinLength)
                                    .putString(AppSecurity.RECOVERY_Q1, q1)
                                    .putString(AppSecurity.RECOVERY_Q2, q2)
                                    .putString(AppSecurity.RECOVERY_A1, AppSecurity.answerTag(1, a1))
                                    .putString(AppSecurity.RECOVERY_A2, AppSecurity.answerTag(2, a2))
                                    .commit()
                            }
                                .onSuccess { hasPin = true }
                        }
                    }
                }
            }
        }
    }

    private fun serviceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val component = ComponentName(this, AppLockAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo.serviceInfo.packageName == component.packageName && it.resolveInfo.serviceInfo.name == component.className
        }
    }
}

private enum class AppFilter { ALL, LOCKED, UNLOCKED }

@Composable
private fun Dashboard(
    isServiceEnabled: () -> Boolean,
    openAccessibilitySettings: () -> Unit,
    openRecoverySetup: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(AppSecurity.PREFS, Context.MODE_PRIVATE) }
    var serviceEnabled by remember { mutableStateOf(isServiceEnabled()) }
    var selected by remember { mutableStateOf(prefs.getStringSet(AppSecurity.LOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceEnabled = isServiceEnabled()
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { AppCatalog.load(context) } }

    val filteredApps = apps.filter { app ->
        val matchesSearch = app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
        val matchesFilter = when (filter) {
            AppFilter.ALL -> true
            AppFilter.LOCKED -> app.packageName in selected
            AppFilter.UNLOCKED -> app.packageName !in selected
        }
        matchesSearch && matchesFilter
    }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(Modifier.size(42.dp), shape = CircleShape, color = LockColors.Primary) {
                Box(contentAlignment = Alignment.Center) {
                    Text("AL", color = LockColors.OnPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("App Lock", color = LockColors.OnSurface, fontSize = 23.sp, fontWeight = FontWeight.Medium)
                Text("Your privacy, protected", color = LockColors.Muted, fontSize = 13.sp)
            }
            Surface(shape = CircleShape, color = LockColors.SurfaceHigh) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("•", fontSize = 14.sp, color = LockColors.Teal)
                    Spacer(Modifier.width(6.dp))
                    Text("${selected.size}", color = LockColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
        }

        if (!prefs.contains(AppSecurity.RECOVERY_Q1)) {
            TextButton(onClick = openRecoverySetup, modifier = Modifier.align(Alignment.End)) { Text("Set up PIN recovery") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search installed apps", color = LockColors.Muted) },
            leadingIcon = { Image(painterResource(R.drawable.search_reference), contentDescription = null, modifier = Modifier.size(28.dp)) },
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LockColors.SurfaceLow,
                unfocusedContainerColor = LockColors.SurfaceLow,
                focusedBorderColor = LockColors.Primary,
                unfocusedBorderColor = LockColors.Outline,
                focusedTextColor = LockColors.OnSurface,
                unfocusedTextColor = LockColors.OnSurface,
                cursorColor = LockColors.Primary
            )
        )

        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == AppFilter.ALL, onClick = { filter = AppFilter.ALL }, label = { Text("All (${apps.size})") })
            FilterChip(selected = filter == AppFilter.LOCKED, onClick = { filter = AppFilter.LOCKED }, label = { Text("Locked (${selected.size})") })
            FilterChip(selected = filter == AppFilter.UNLOCKED, onClick = { filter = AppFilter.UNLOCKED }, label = { Text("Unlocked (${apps.size - selected.size})") })
        }

        if (!serviceEnabled) Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = LockColors.SurfaceLow),
            border = BorderStroke(1.dp, LockColors.Outline)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(40.dp), shape = CircleShape, color = LockColors.SurfaceHigh) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("i", color = LockColors.Primary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("App monitoring", color = LockColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(if (serviceEnabled) "Protection service is enabled" else "Enable to protect selected apps", color = if (serviceEnabled) LockColors.Teal else LockColors.Muted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "App Lock checks which app is in the foreground so it can show your lock screen. It does not read screen content or send data off this phone. You can turn the service off in Android Settings.",
                    color = LockColors.Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = openAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = LockColors.SurfaceHigh, contentColor = LockColors.Primary)
                ) {
                    Text(if (serviceEnabled) "Manage Accessibility access" else "Enable app monitoring")
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(8.dp))
            Text("INSTALLED APPLICATIONS", color = LockColors.Muted, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("${selected.size} protected", color = LockColors.Primary, fontSize = 13.sp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 10.dp)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                val isLocked = app.packageName in selected
                AppRow(app, isLocked) { checked ->
                    selected = if (checked) selected + app.packageName else selected - app.packageName
                    prefs.edit().putStringSet(AppSecurity.LOCKED_PACKAGES, selected).apply()
                }
            }
            if (filteredApps.isEmpty()) item {
                Text("No apps match this filter.", Modifier.fillMaxWidth().padding(32.dp), color = LockColors.Muted, textAlign = TextAlign.Center)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
            shape = CircleShape,
            color = LockColors.SurfaceHigh,
            border = BorderStroke(1.dp, LockColors.Outline)
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(9.dp), shape = CircleShape, color = if (serviceEnabled && selected.isNotEmpty()) LockColors.Teal else LockColors.Muted) {}
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        selected.isEmpty() -> "Choose apps to protect"
                        serviceEnabled -> "${selected.size} apps protected"
                        else -> "${selected.size} apps selected · service off"
                    },
                    color = LockColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f)
                )
                Text(if (selected.isNotEmpty()) "●" else "○", color = LockColors.Primary, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, isLocked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!isLocked) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = LockColors.SurfaceLow),
        border = BorderStroke(1.dp, if (isLocked) Color(0xFF334253) else LockColors.Outline)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = LockColors.Surface) {
                Box(contentAlignment = Alignment.Center) {
                    if (app.icon != null) {
                        Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(42.dp))
                    } else {
                        Text(app.label.take(1).uppercase(), color = if (isLocked) LockColors.Teal else LockColors.Muted, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = LockColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(if (isLocked) "Protected by App Lock" else app.packageName, color = if (isLocked) LockColors.Teal else LockColors.Muted, fontSize = 11.sp, maxLines = 1)
            }
            Switch(checked = isLocked, onCheckedChange = onCheckedChange)
        }
    }
}

private data class InstalledApp(val packageName: String, val label: String, val icon: ImageBitmap?)

internal fun loadAppIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
}.getOrNull()

private object AppCatalog {
    fun load(context: Context): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .map { info ->
                val packageName = info.activityInfo.packageName
                val icon = loadAppIcon(context, packageName)
                InstalledApp(packageName, info.loadLabel(context.packageManager).toString(), icon)
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

@Composable
private fun PinSetup(onCreated: (String, Int, String, String, String, String) -> Unit) {
    var stage by remember { mutableIntStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var pinLength by remember { mutableIntStateOf(4) }
    var error by remember { mutableStateOf("") }
    var q1 by remember { mutableStateOf("") }; var a1 by remember { mutableStateOf("") }
    var q2 by remember { mutableStateOf("") }; var a2 by remember { mutableStateOf("") }
    if (stage == 2) {
        RecoveryQuestions(q1, { q1 = it }, a1, { a1 = it }, q2, { q2 = it }, a2, { a2 = it }, error) {
            if (q1.isBlank() || a1.isBlank() || q2.isBlank() || a2.isBlank()) error = "Fill in both questions and answers."
            else if (q1.trim().equals(q2.trim(), true)) error = "Choose two different questions."
            else onCreated(firstPin, pinLength, q1.trim(), a1.trim(), q2.trim(), a2.trim())
        }
    } else PinPad(
        title = if (stage == 0) "Create your PIN" else "Confirm your PIN",
        subtitle = if (stage == 0) "Choose a $pinLength-digit PIN" else "Enter your PIN once more",
        error = error, pinLength = pinLength,
        onPinLengthChange = if (stage == 0) { { pinLength = it; error = "" } } else null,
        onDigit = { pin ->
            if (stage == 0) { firstPin = pin; stage = 1; error = "" }
            else if (pin == firstPin) { stage = 2; error = "" }
            else { stage = 0; error = "PINs did not match. Start again." }
        }
    )
}

@Composable
internal fun RecoveryQuestions(q1: String, onQ1: (String) -> Unit, a1: String, onA1: (String) -> Unit,
                              q2: String, onQ2: (String) -> Unit, a2: String, onA2: (String) -> Unit,
                              error: String, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Recovery questions", color = LockColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp)); Text("Answers stay on this phone. Use answers you can remember exactly.", color = LockColors.Muted)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(q1, onQ1, Modifier.fillMaxWidth(), label = { Text("Question 1") }, placeholder = { Text("e.g. What was my first school?") })
        OutlinedTextField(a1, onA1, Modifier.fillMaxWidth(), label = { Text("Answer 1") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(q2, onQ2, Modifier.fillMaxWidth(), label = { Text("Question 2") }, placeholder = { Text("e.g. What is my favorite book?") })
        OutlinedTextField(a2, onA2, Modifier.fillMaxWidth(), label = { Text("Answer 2") }, singleLine = true)
        Text(error, color = LockColors.Error, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = CircleShape) { Text("Save recovery options") }
    }
}

@Composable
internal fun PinPad(
    title: String,
    subtitle: String,
    error: String = "",
    biometricAction: (() -> Unit)? = null,
    appIcon: ImageBitmap? = null,
    pinLength: Int = 6,
    onPinLengthChange: ((Int) -> Unit)? = null,
    recoveryAction: (() -> Unit)? = null,
    onDigit: (String) -> Unit
) {
    var pin by remember(title) { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(4.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(72.dp), shape = RoundedCornerShape(18.dp), color = LockColors.SurfaceHigh) {
                Box(contentAlignment = Alignment.Center) {
                    if (appIcon != null) {
                        Image(bitmap = appIcon, contentDescription = title, modifier = Modifier.fillMaxSize())
                        Surface(Modifier.align(Alignment.BottomEnd).size(24.dp), shape = CircleShape, color = LockColors.Primary) {
                            Image(painterResource(R.drawable.lock_badge_reference), contentDescription = "Locked", modifier = Modifier.fillMaxSize())
                        }
                    } else Text("AL", color = LockColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(title, color = LockColors.OnSurface, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = LockColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(pinLength) { index ->
                    Surface(
                        Modifier.size(13.dp), shape = CircleShape,
                        color = if (index < pin.length) LockColors.Primary else Color.Transparent,
                        border = if (index < pin.length) null else BorderStroke(1.5.dp, LockColors.SurfaceHighest)
                    ) {}
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(error, color = LockColors.Error, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (biometricAction != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = biometricAction,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = LockColors.SurfaceHigh, contentColor = LockColors.Teal)
                ) {
                    Text("Use biometric unlock")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { key ->
                        if (key.isEmpty() && onPinLengthChange != null) {
                            Button(onClick = { onPinLengthChange(if (pinLength == 4) 6 else 4); pin = "" }, modifier = Modifier.size(68.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = LockColors.SurfaceHigh, contentColor = LockColors.Primary)) { Text("${if (pinLength == 4) 6 else 4}", fontSize = 20.sp) }
                        } else if (key.isEmpty() && biometricAction != null) {
                            Button(onClick = biometricAction, modifier = Modifier.size(68.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = LockColors.Teal)) {
                                Image(painterResource(R.drawable.fingerprint_reference), contentDescription = "Use fingerprint", modifier = Modifier.size(34.dp))
                            }
                        } else if (key.isEmpty()) Spacer(Modifier.size(68.dp))
                        else if (key == "⌫") {
                            Button(
                                onClick = { pin = pin.dropLast(1) }, modifier = Modifier.size(68.dp), shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = LockColors.Surface, contentColor = LockColors.Muted)
                            ) { Image(painterResource(R.drawable.backspace_reference), contentDescription = "Delete last digit", modifier = Modifier.size(30.dp)) }
                        } else {
                            Button(
                                onClick = {
                                    if (pin.length < pinLength) {
                                        pin += key
                                        if (pin.length == pinLength) { val completedPin = pin; pin = ""; onDigit(completedPin) }
                                    }
                                }, modifier = Modifier.size(68.dp), shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = LockColors.Surface, contentColor = LockColors.OnSurface)
                            ) { Text(key, fontSize = 23.sp, fontWeight = FontWeight.Normal) }
                        }
                    }
                }
            }
        }
        if (recoveryAction != null) Button(onClick = recoveryAction, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = LockColors.Primary)) { Text("Forgot PIN?") }
        else Text(if (onPinLengthChange != null) "PIN is stored securely on this device" else "", color = LockColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
