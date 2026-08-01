package com.trombadario.ui.awayfromhome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.trombadario.R
import com.trombadario.ui.components.MessageScreen

/**
 * Replaces the whole navigation graph whenever the gate is not [AtHome] - a root
 * swap, not a dialog on top, so there is never a screen with data rendered
 * underneath it.
 */
@Composable
fun AwayFromHomeScreen(
    wrongServer: Boolean,
    onRetry: () -> Unit,
    onChangeServer: () -> Unit,
) {
    MessageScreen(
        icon = if (wrongServer) Icons.Default.Home else Icons.Default.WifiOff,
        title = stringResource(R.string.away_title),
        message = stringResource(
            if (wrongServer) R.string.server_setup_error_not_trombadario else R.string.away_message
        ),
        actionLabel = stringResource(R.string.action_retry),
        onAction = onRetry,
        secondaryLabel = stringResource(R.string.settings_change_server),
        onSecondary = onChangeServer,
    )
}
