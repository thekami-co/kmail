package com.kmail.thekami.ui.setup

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kmail.thekami.R
import com.kmail.thekami.ui.components.AppIcons
import com.kmail.thekami.ui.components.HOW_TO_USE_URL
import com.kmail.thekami.ui.components.KmailWordmark
import com.kmail.thekami.ui.components.LegalLinks
import com.kmail.thekami.ui.components.openLinkAction

private val FieldShape = RoundedCornerShape(16.dp)

@Composable
fun CredentialsScreen(
    state: CredentialsUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onContinue: () -> Unit,
    onGoogle: () -> Unit,
    onScanQr: () -> Unit,
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showOtherWays by rememberSaveable { mutableStateOf(false) }
    // false = choose Google or other email, true = email and password form.
    var showForm by rememberSaveable { mutableStateOf(false) }
    val openLink = openLinkAction()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Entrance animation: skipped when system animations are off, and not replayed on rotation.
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    var shown by rememberSaveable { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { shown = true }

    val goBack = {
        focusManager.clearFocus()
        keyboard?.hide()
        showForm = false
    }
    BackHandler(enabled = showForm) { goBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        AnimatedContent(
            targetState = showForm,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val offset = if (targetState) 1 else -1
                (
                    slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 6 * offset } +
                        fadeIn(tween(300))
                    ) togetherWith (
                    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 6 * offset } +
                        fadeOut(tween(150))
                    )
            },
            label = "loginStep",
        ) { form ->
            if (!form) {
                ChooseStep(
                    shown = shown,
                    onHelp = { showHelp = true },
                    onGoogle = onGoogle,
                    onOther = { showForm = true },
                    onOtherWays = { showOtherWays = true },
                )
            } else {
                FormStep(
                    state = state,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onContinue = onContinue,
                    onBack = goBack,
                    onHelp = { showHelp = true },
                )
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.credentials_help_title)) },
            text = { Text(stringResource(R.string.credentials_help_body)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.credentials_help_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { openLink(HOW_TO_USE_URL) }) {
                    Text(stringResource(R.string.legal_how_to_use))
                }
            },
        )
    }

    if (showOtherWays) {
        OtherWaysDialog(
            onDismiss = { showOtherWays = false },
            onScanQr = {
                showOtherWays = false
                onScanQr()
            },
            onImportFile = {
                showOtherWays = false
                onImportFile()
            },
        )
    }
}

// ---- step 1: Google or other email ----

@Composable
private fun ChooseStep(
    shown: Boolean,
    onHelp: () -> Unit,
    onGoogle: () -> Unit,
    onOther: () -> Unit,
    onOtherWays: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        TopBar(onHelp = onHelp, onBack = null)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Reveal(shown, 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.credentials_title),
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(R.string.login_choose_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Reveal(shown, 1) {
                    Column(Modifier.padding(top = 24.dp)) {
                        ProviderRow(
                            leading = { Icon(GoogleG, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp)) },
                            title = stringResource(R.string.provider_google_title),
                            subtitle = stringResource(R.string.provider_google_sub),
                            badge = stringResource(R.string.login_recommended),
                            onClick = onGoogle,
                        )
                        ProviderRow(
                            leading = {
                                Icon(Icons.Rounded.AlternateEmail, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            },
                            title = stringResource(R.string.provider_other_title),
                            subtitle = stringResource(R.string.provider_other_sub),
                            onClick = onOther,
                            showDivider = false,
                        )
                    }
                }
                Reveal(shown, 2) {
                    Column(Modifier.padding(top = 16.dp)) {
                        TextButton(
                            onClick = onOtherWays,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.login_other_ways), style = MaterialTheme.typography.labelLarge)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.login_privacy_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        // No keyboard on this step, so the links can stay pinned to the bottom.
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            LegalLinks()
        }
    }
}

@Composable
private fun ProviderRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: String? = null,
    showDivider: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(scheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) { leading() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (badge != null) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
        if (showDivider) HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = scheme.outlineVariant)
    }
}

// ---- step 2: email and password ----

@Composable
private fun FormStep(
    state: CredentialsUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onHelp: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        TopBar(onHelp = onHelp, onBack = onBack)
        // The links live inside the scrolling area: they must not ride up with the keyboard.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.credentials_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.credentials_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                LoginField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(R.string.credentials_email_label),
                    placeholder = "name@example.com",
                    icon = Icons.Rounded.Mail,
                    errorText = if (state.emailError) stringResource(R.string.credentials_email_error) else null,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.padding(top = 24.dp).focusRequester(focus),
                )
                LoginField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.login_password_label),
                    placeholder = null,
                    icon = Icons.Rounded.Lock,
                    errorText = if (state.passwordError) stringResource(R.string.login_password_error) else null,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onDone = onContinue,
                    isPassword = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = onContinue,
                    modifier = Modifier.padding(top = 20.dp).fillMaxWidth().heightIn(min = 56.dp),
                    shape = FieldShape,
                ) {
                    Text(stringResource(R.string.credentials_continue), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(32.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LegalLinks() }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TopBar(onHelp: () -> Unit, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.login_back))
            }
        } else {
            Box(Modifier.padding(start = 12.dp)) { KmailWordmark() }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onHelp) {
            Icon(
                imageVector = AppIcons.Info,
                contentDescription = stringResource(R.string.credentials_help_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fades in and slides up a little, one step after the previous section. */
@Composable
private fun Reveal(visible: Boolean, index: Int, content: @Composable () -> Unit) {
    val delay = index * 70
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxWidth(),
        enter = fadeIn(tween(420, delay, FastOutSlowInEasing)) +
            slideInVertically(tween(420, delay, FastOutSlowInEasing)) { it / 10 },
    ) {
        content()
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String?,
    icon: ImageVector,
    errorText: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = true,
        isError = errorText != null,
        shape = FieldShape,
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.login_hide_password else R.string.login_show_password,
                        ),
                    )
                }
            }
        } else {
            null
        },
        supportingText = if (errorText != null) {
            { Text(errorText) }
        } else {
            null
        },
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun OtherWaysDialog(
    onDismiss: () -> Unit,
    onScanQr: () -> Unit,
    onImportFile: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_other_ways)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.login_other_ways_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                WayRow(AppIcons.QrScan, stringResource(R.string.credentials_scan_qr), onScanQr)
                WayRow(AppIcons.FolderOpen, stringResource(R.string.credentials_import_file), onImportFile)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.credentials_help_close))
            }
        },
    )
}

@Composable
private fun WayRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The four-colour Google "G", drawn as a vector so no image file is needed. */
private val GoogleG: ImageVector by lazy {
    ImageVector.Builder(
        name = "GoogleG",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 48f,
        viewportHeight = 48f,
    ).apply {
        addPath(
            PathParser().parsePathString(
                "M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11c4.16-3.83 6.56-9.47 6.56-16.17z",
            ).toNodes(),
            fill = SolidColor(Color(0xFF4285F4)),
        )
        addPath(
            PathParser().parsePathString(
                "M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7C7.96 41.07 15.4 46 24 46z",
            ).toNodes(),
            fill = SolidColor(Color(0xFF34A853)),
        )
        addPath(
            PathParser().parsePathString(
                "M11.69 28.18C11.25 26.86 11 25.45 11 24s.25-2.86.69-4.18v-5.7H4.34C2.85 17.09 2 20.45 2 24c0 3.55.85 6.91 2.34 9.88l7.35-5.7z",
            ).toNodes(),
            fill = SolidColor(Color(0xFFFBBC05)),
        )
        addPath(
            PathParser().parsePathString(
                "M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z",
            ).toNodes(),
            fill = SolidColor(Color(0xFFEA4335)),
        )
    }.build()
}
