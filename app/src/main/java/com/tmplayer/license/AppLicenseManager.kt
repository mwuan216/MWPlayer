package com.tmplayer.license

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quản lý dùng thử 3 ngày và kích hoạt bản quyền trọn đời cho MWPlayer.
 * - Khi cài app lần đầu sẽ không báo gì trong suốt 3 ngày đầu.
 * - Khi dùng hết 3 ngày mà chưa kích hoạt sẽ hiển thị màn hình yêu cầu nhập Key Active.
 * - Key Active hợp lệ: MWPLAYER_LIFETIME_216
 */
object AppLicenseManager {
    private const val PREFS_NAME = "mwplayer_license_prefs"
    private const val KEY_FIRST_LAUNCH_MS = "first_launch_ms"
    private const val KEY_IS_ACTIVATED = "is_activated"
    private const val KEY_ACTIVATED_CODE = "activated_code"

    const val VALID_ACTIVE_KEY = "MWPLAYER_LIFETIME_216"
    const val TRIAL_DURATION_MS = 3L * 24 * 60 * 60 * 1000L // 3 ngày

    private val _isActivatedFlow = MutableStateFlow(false)
    val isActivatedFlow: StateFlow<Boolean> = _isActivatedFlow.asStateFlow()

    private val _isTrialExpiredFlow = MutableStateFlow(false)
    val isTrialExpiredFlow: StateFlow<Boolean> = _isTrialExpiredFlow.asStateFlow()

    private var initialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun init(context: Context) {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        var firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH_MS, 0L)
        if (firstLaunch <= 0L) {
            firstLaunch = now
            prefs.edit().putLong(KEY_FIRST_LAUNCH_MS, firstLaunch).apply()
        }
        val activated = prefs.getBoolean(KEY_IS_ACTIVATED, false)
        _isActivatedFlow.value = activated
        _isTrialExpiredFlow.value = !activated && (now - firstLaunch >= TRIAL_DURATION_MS)
        initialized = true
    }

    @Synchronized
    fun refreshStatus(context: Context) {
        init(context)
    }

    fun isRequiresActivation(context: Context): Boolean {
        if (!initialized) init(context)
        return !_isActivatedFlow.value && _isTrialExpiredFlow.value
    }

    @Synchronized
    fun verifyAndActivate(context: Context, inputKey: String): Boolean {
        init(context)
        val cleaned = inputKey.trim()
        if (cleaned.equals(VALID_ACTIVE_KEY, ignoreCase = true)) {
            getPrefs(context).edit()
                .putBoolean(KEY_IS_ACTIVATED, true)
                .putString(KEY_ACTIVATED_CODE, VALID_ACTIVE_KEY)
                .apply()
            _isActivatedFlow.value = true
            _isTrialExpiredFlow.value = false
            return true
        }
        return false
    }
}

@Composable
fun ActivationGateScreen(
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var keyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val inputFocus = remember { FocusRequester() }

    fun submit() {
        if (keyInput.isBlank()) {
            errorMessage = "Vui lòng nhập mã kích hoạt!"
            return
        }
        if (AppLicenseManager.verifyAndActivate(context, keyInput)) {
            errorMessage = null
            focusManager.clearFocus()
            onActivated()
        } else {
            errorMessage = "Mã kích hoạt không hợp lệ. Vui lòng kiểm tra lại!"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .imePadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF151A24))
                .border(1.dp, Tone.accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Tone.accent.copy(alpha = 0.16f))
                    .border(1.5.dp, Tone.accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TmIcons.Lock,
                    contentDescription = null,
                    tint = Tone.accent,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Kích hoạt MWPlayer",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Thời gian dùng thử 3 ngày đã kết thúc.\nVui lòng nhập mã kích hoạt (Key Active) để tiếp tục sử dụng ứng dụng.",
                color = Color(0xFFB0B8C8),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            OutlinedTextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it
                    if (errorMessage != null) errorMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocus),
                placeholder = {
                    Text(
                        text = "Nhập mã kích hoạt...",
                        color = Color(0xFF6E778A)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0E121A),
                    unfocusedContainerColor = Color(0xFF0E121A),
                    focusedBorderColor = Tone.accent,
                    unfocusedBorderColor = Color(0xFF2B3448),
                    cursorColor = Tone.accent
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit() }
                )
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                        if (!clipText.isNullOrBlank()) {
                            keyInput = clipText
                            errorMessage = null
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Dán mã", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = { submit() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tone.accent,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = TmIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Kích hoạt ngay",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
