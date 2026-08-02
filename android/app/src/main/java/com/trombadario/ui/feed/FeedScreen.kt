package com.trombadario.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.TrombadiceDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.components.MessageScreen
import com.trombadario.ui.components.formatDateTime
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    container: AppContainer,
    currentUser: UserDto,
    onOpenTrombadice: (Int) -> Unit,
    onNewTrombadice: () -> Unit,
) {
    val viewModel: FeedViewModel = viewModel(
        factory = viewModelFactory { FeedViewModel(container, currentUser) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Covers both entering the screen and coming back to it - after saving a new
    // event, after editing one, after deleting one from the detail screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feed_title)) }) },
        floatingActionButton = {
            if (currentUser.isAdmin) {
                FloatingActionButton(onClick = onNewTrombadice) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.feed_new))
                }
            }
        },
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            when {
                state.loading -> LoadingScreen()

                state.error -> MessageScreen(
                    icon = Icons.Default.SentimentDissatisfied,
                    title = stringResource(R.string.feed_error),
                    message = "",
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::load,
                )

                else -> Column(Modifier.fillMaxSize()) {
                    if (currentUser.isAdmin && state.children.size > 1) {
                        ChildFilter(
                            children = state.children,
                            selectedChildId = state.selectedChildId,
                            onSelect = viewModel::selectChild,
                        )
                    }

                    if (state.events.isEmpty()) {
                        MessageScreen(
                            icon = Icons.Default.SentimentDissatisfied,
                            title = stringResource(
                                if (currentUser.isAdmin) R.string.feed_empty_admin else R.string.feed_empty
                            ),
                            message = "",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.events, key = { it.id }) { event ->
                                EventCard(
                                    event = event,
                                    childName = if (currentUser.isAdmin && state.children.size > 1) {
                                        viewModel.displayNameOf(event.childId)
                                    } else {
                                        null
                                    },
                                    onClick = { onOpenTrombadice(event.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildFilter(
    children: List<UserDto>,
    selectedChildId: Int?,
    onSelect: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedChildId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.feed_filter_all)) },
        )
        children.forEach { child ->
            FilterChip(
                selected = selectedChildId == child.id,
                onClick = { onSelect(child.id) },
                label = { Text(child.displayName) },
            )
        }
    }
}

@Composable
private fun EventCard(event: TrombadiceDto, childName: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = parseInstant(event.occurredAt).formatDateTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (childName != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = childName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
