package com.kmail.thekami.ui.compose

import com.kmail.thekami.ui.components.AppIcons
import android.Manifest
import android.content.pm.PackageManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmail.thekami.R
import com.kmail.thekami.data.contacts.Suggestion
import com.kmail.thekami.ui.settings.RichOps
import com.kmail.thekami.ui.setup.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ComposeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDiscard by remember { mutableStateOf(false) }
    var fromMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var subjectFocused by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf(TextFieldValue()) }
    val sentMessage = stringResource(R.string.compose_sent)
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshSuggestions() }
    val focusField = { kind: RecipientKind ->
        vm.onFocusField(kind)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED && vm.takeContactsPrompt()) {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    // The page eases in once the draft is ready.
    val appear by animateFloatAsState(
        targetValue = if (state.loading) 0f else 1f,
        animationSpec = tween(240),
        label = "compose-appear",
    )

    // Put the cursor at the top of the prefilled body, above the quote.
    LaunchedEffect(state.loading) {
        if (!state.loading) body = TextFieldValue(state.body, TextRange.Zero)
    }
    LaunchedEffect(state.sent) {
        if (state.sent) {
            Toast.makeText(context, sentMessage, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    LaunchedEffect(state.closeRequested) { if (state.closeRequested) onClose() }

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addAttachments(uris)
    }
    // Leaving keeps the draft: it is autosaved and listed under "Unsent drafts".
    val leave = {
        vm.leave()
        onClose()
    }
    BackHandler(enabled = !state.sending) { leave() }
    val canSend = !state.sending && !state.loading

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (vm.mode) {
                                "reply", "replyall" -> R.string.compose_reply
                                "forward" -> R.string.compose_forward
                                "draft" -> R.string.compose_draft
                                else -> R.string.compose_new
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.reader_back))
                    }
                },
                actions = {
                    IconButton(onClick = { attachLauncher.launch(arrayOf("*/*")) }) {
                        Icon(AppIcons.Attachment, contentDescription = stringResource(R.string.compose_attach))
                    }
                    Button(
                        onClick = vm::send,
                        enabled = canSend,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        AnimatedContent(targetState = state.sending, label = "send-icon") { sending ->
                            if (sending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = LocalContentColor.current,
                                )
                            } else {
                                Icon(AppIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.compose_send))
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(AppIcons.More, contentDescription = stringResource(R.string.home_more_options))
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.compose_discard_confirm)) },
                                onClick = {
                                    moreMenu = false
                                    showDiscard = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .graphicsLayer { this.alpha = appear }
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.sending) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let { error ->
                val text = when (error) {
                    ComposeError.NoRecipient -> stringResource(R.string.compose_error_no_recipient)
                    is ComposeError.InvalidRecipient -> stringResource(R.string.compose_error_invalid_recipient, error.text)
                    ComposeError.AttachmentTooLarge -> stringResource(R.string.compose_error_too_large)
                    is ComposeError.SendFailed -> error.error?.let {
                        stringResource(R.string.compose_error_send, stringResource(it.messageRes()))
                    } ?: stringResource(R.string.compose_error_no_account)
                }
                Text(
                    text,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // From
            val canSwitch = state.accounts.size > 1
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(enabled = canSwitch) { fromMenu = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel(R.string.compose_from)
                Box(Modifier.weight(1f)) {
                    val selected = state.accounts.firstOrNull { it.id == state.fromId }
                    Text(
                        selected?.emailAddress.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }) {
                        state.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.emailAddress) },
                                onClick = {
                                    fromMenu = false
                                    vm.onFrom(account.id)
                                },
                            )
                        }
                    }
                }
                if (canSwitch) {
                    Icon(
                        AppIcons.ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FieldDivider()

            RecipientRow(R.string.compose_to, state.to, vm::onTo, onFocus = { focusField(RecipientKind.TO) }) {
                if (!state.showCcBcc) {
                    Text(
                        stringResource(R.string.compose_show_cc),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = vm::showCcBcc)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = state.showCcBcc,
                enter = expandVertically(tween(240)) + fadeIn(tween(240)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
            ) {
                Column {
                    RecipientRow(R.string.compose_cc, state.cc, vm::onCc, onFocus = { focusField(RecipientKind.CC) })
                    RecipientRow(R.string.compose_bcc, state.bcc, vm::onBcc, onFocus = { focusField(RecipientKind.BCC) })
                }
            }
            SuggestionPanel(state.suggestions, vm::pickSuggestion)

            // Subject
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlatInput(
                    value = state.subject,
                    onChange = vm::onSubject,
                    modifier = Modifier.weight(1f).onFocusChanged { subjectFocused = it.isFocused },
                    placeholder = stringResource(R.string.compose_subject),
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            FieldDivider(subjectFocused)

            // Attachments, as chips
            AnimatedVisibility(
                visible = state.attachments.isNotEmpty(),
                enter = expandVertically(tween(240)) + fadeIn(tween(240)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
            ) {
                Column {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.attachments.forEach { attachment ->
                            AttachmentChip(attachment) { vm.removeAttachment(attachment) }
                        }
                    }
                    FieldDivider()
                }
            }

            // Body: no box, the page itself is the writing surface.
            BasicTextField(
                value = body,
                onValueChange = {
                    body = it
                    vm.onBody(it.text)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 12,
                decorationBox = { inner ->
                    Box {
                        if (body.text.isEmpty()) {
                            Text(
                                stringResource(R.string.compose_body_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        inner()
                    }
                },
            )

            // The signature of the From address. It is added when the message is sent.
            Crossfade(targetState = state.signatureHtml, animationSpec = tween(220), label = "signature") { html ->
                SignaturePreview(html)
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.compose_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    vm.discard()
                }) { Text(stringResource(R.string.compose_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.compose_discard_keep))
                }
            },
        )
    }
}

/** The signature as it will look in the sent mail, below a thin line. It is not editable here. */
@Composable
private fun SignaturePreview(html: String) {
    if (html.isBlank()) return
    val linkColor = MaterialTheme.colorScheme.primary
    val styled = remember(html, linkColor) {
        val doc = RichOps.fromHtml(html)
        val text = doc.text.trimEnd()
        RichOps.annotate(text, doc.spans, linkColor)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Text(
            stringResource(R.string.settings_signature_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        Text(
            styled,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A file that will be sent: name and size, with a small cross to take it off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentChip(attachment: ComposeAttachment, onRemove: () -> Unit) {
    val context = LocalContext.current
    InputChip(
        selected = false,
        onClick = {},
        label = {
            Text(
                "${attachment.name} · ${Formatter.formatShortFileSize(context, attachment.bytes.size.toLong())}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        },
        leadingIcon = {
            Icon(AppIcons.Attachment, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            Icon(
                AppIcons.Close,
                contentDescription = stringResource(R.string.compose_remove_attachment, attachment.name),
                modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onRemove),
            )
        },
    )
}

/** A thin line under each field. It turns to the main color while that field has focus. */
@Composable
private fun FieldDivider(active: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val color by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.outlineVariant.copy(alpha = 0.6f),
        animationSpec = tween(180),
        label = "divider",
    )
    HorizontalDivider(color = color)
}

@Composable
private fun FieldLabel(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        modifier = Modifier.width(72.dp),
    )
}

/** A borderless single field: just text on the page. */
@Composable
private fun FlatInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        decorationBox = { inner ->
            Box(Modifier.padding(vertical = 12.dp)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun RecipientRow(
    labelRes: Int,
    value: String,
    onChange: (String) -> Unit,
    onFocus: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldLabel(labelRes)
        FlatInput(
            value = value,
            onChange = onChange,
            modifier = Modifier.weight(1f).onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
            ),
        )
        trailing()
    }
    FieldDivider(focused)
}

@Composable
private fun SuggestionPanel(suggestions: List<Suggestion>, onPick: (Suggestion) -> Unit) {
    if (suggestions.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        suggestions.forEach { s ->
            Column(
                Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(s.name ?: s.address, style = MaterialTheme.typography.bodyMedium)
                if (s.name != null) {
                    Text(
                        s.address,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FieldDivider()
        }
    }
}
