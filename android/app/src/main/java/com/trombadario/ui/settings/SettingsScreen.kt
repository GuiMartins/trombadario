package com.trombadario.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.AppTopBar
import com.trombadario.ui.theme.ThemePreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    currentUser: UserDto,
    onOpenUsers: () -> Unit,
    onOpenSplashMessages: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val theme by container.serverConfigStore.theme
        .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)
    val baseUrl by container.serverConfigStore.baseUrl.collectAsStateWithLifecycle(initialValue = null)

    var confirmingServerChange by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(title = stringResource(R.string.settings_title), currentUser = currentUser) }
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                SectionTitle(stringResource(R.string.settings_section_account))
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(currentUser.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${currentUser.username} · " + stringResource(
                            if (currentUser.isAdmin) R.string.users_role_admin
                            else R.string.users_role_child
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (currentUser.isAdmin) {
                    // Fora da nav bar de propósito: cinco abas é o limite do
                    // Material3 e fica apertado no celular.
                    ActionRow(
                        icon = Icons.Default.People,
                        label = stringResource(R.string.users_title),
                        onClick = onOpenUsers,
                    )
                    ActionRow(
                        icon = Icons.Default.ChatBubbleOutline,
                        label = stringResource(R.string.splash_title),
                        onClick = onOpenSplashMessages,
                    )
                    ActionRow(
                        icon = Icons.Default.InsertChart,
                        label = stringResource(R.string.report_title),
                        onClick = onOpenReport,
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(stringResource(R.string.settings_section_appearance))
                ThemePreference.entries.forEach { option ->
                    ThemeRow(
                        option = option,
                        selected = theme == option,
                        onSelect = { scope.launch { container.serverConfigStore.setTheme(option) } },
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(stringResource(R.string.settings_section_server))
                Text(
                    text = baseUrl.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                ActionRow(
                    icon = Icons.Default.Dns,
                    label = stringResource(R.string.settings_change_server),
                    onClick = { confirmingServerChange = true },
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ActionRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = stringResource(R.string.settings_logout),
                    onClick = { container.repository.logout() },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmingServerChange) {
        AlertDialog(
            onDismissRequest = { confirmingServerChange = false },
            title = { Text(stringResource(R.string.settings_change_server)) },
            text = { Text(stringResource(R.string.settings_change_server_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingServerChange = false
                    scope.launch {
                        container.repository.logout()
                        container.serverConfigStore.clearBaseUrl()
                        container.homeNetworkGate.refresh()
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingServerChange = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun ThemeRow(option: ThemePreference, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // selectable with Role.RadioButton so a screen reader announces the
            // group correctly; clickable alone would not.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(
                when (option) {
                    ThemePreference.SYSTEM -> R.string.settings_theme_system
                    ThemePreference.LIGHT -> R.string.settings_theme_light
                    ThemePreference.DARK -> R.string.settings_theme_dark
                }
            )
        )
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label)
    }
}
