package com.kmail.thekami.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmail.thekami.R
import com.kmail.thekami.data.prefs.SignatureState
import com.kmail.thekami.data.prefs.SignatureStore
import com.kmail.thekami.domain.model.Account
import com.kmail.thekami.ui.components.AppIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SignatureViewModel @Inject constructor(private val store: SignatureStore) : ViewModel() {
    val state: StateFlow<SignatureState> = store.state
    fun setMobileEnabled(on: Boolean) = store.setEnabled(on)
    fun setMobileText(text: String) = store.setDefault(text)
    fun emailHtml(accountId: String): String = store.emailHtmlFor(accountId)
    fun displayName(accountId: String): String? = store.displayNameFor(accountId)
    fun saveEmail(accountId: String, plain: String, html: String, displayName: String) {
        store.setEmailSignature(accountId, plain, html)
        store.setDisplayName(accountId, displayName)
    }
}

private const val PAGE_HUB = "hub"
private const val PAGE_EMAIL = "email"
private const val PAGE_MOBILE = "mobile"

/** The signature settings: a list with the email signature and the mobile signature, each opening its own page. */
@Composable
fun SignatureSection(accounts: List<Account>, vm: SignatureViewModel = hiltViewModel()) {
    var page by rememberSaveable { mutableStateOf(PAGE_HUB) }
    BackHandler(enabled = page != PAGE_HUB) { page = PAGE_HUB }

    when (page) {
        PAGE_EMAIL -> EmailSignaturePage(accounts, vm, onDone = { page = PAGE_HUB })
        PAGE_MOBILE -> MobileSignaturePage(vm)
        else -> {
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsGroup(
                title = null,
                entries = listOf(
                    SettingsEntry(
                        AppIcons.NoteEdit,
                        stringResource(R.string.sig_email_title),
                        stringResource(R.string.sig_email_summary),
                        onClick = { page = PAGE_EMAIL },
                    ),
                    SettingsEntry(
                        AppIcons.MailOpen,
                        stringResource(R.string.sig_mobile_title),
                        stringResource(if (state.enabled) R.string.sig_on else R.string.sig_off),
                        onClick = { page = PAGE_MOBILE },
                    ),
                ),
            )
        }
    }
}

@Composable
private fun EmailSignaturePage(accounts: List<Account>, vm: SignatureViewModel, onDone: () -> Unit) {
    if (accounts.isEmpty()) {
        Text(stringResource(R.string.sig_no_accounts), modifier = Modifier.padding(24.dp))
        return
    }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var selectedId by rememberSaveable { mutableStateOf(accounts.first().id) }
    val account = accounts.firstOrNull { it.id == selectedId } ?: accounts.first()
    var menu by remember { mutableStateOf(false) }
    val editor = remember(account.id) { RichEditorState(RichOps.fromHtml(vm.emailHtml(account.id))) }
    var name by remember(account.id) { mutableStateOf(vm.displayName(account.id) ?: account.displayName) }
    val saved = stringResource(R.string.sig_saved)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box {
            OutlinedTextField(
                value = account.emailAddress,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(R.string.sig_address)) },
                trailingIcon = { Icon(AppIcons.ChevronDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.matchParentSize().clickable { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                accounts.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.emailAddress) },
                        onClick = {
                            selectedId = item.id
                            menu = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(stringResource(R.string.sig_display_name)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, scheme.outline, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)),
        ) {
            RichEditor(editor, stringResource(R.string.sig_placeholder))
            HorizontalDivider(color = scheme.outlineVariant)
            RichToolbar(editor, Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp))
        }

        Button(
            onClick = {
                vm.saveEmail(account.id, editor.plain(), editor.html(), name)
                Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
                onDone()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.settings_pin_save)) }
    }
}

@Composable
private fun MobileSignaturePage(vm: SignatureViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(state.defaultText) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = state.enabled, role = Role.Switch, onValueChange = { vm.setMobileEnabled(it) })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.sig_mobile_switch),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            )
            Switch(checked = state.enabled, onCheckedChange = null)
        }
        if (state.enabled) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    vm.setMobileText(it)
                },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        Text(
            stringResource(R.string.sig_mobile_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
