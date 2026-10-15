package com.ivor.ivormusic.ui.podcast

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PodcastFeed

/**
 * The Podcasts tab: a list of subscribed feeds. Tapping a feed opens its
 * detail page (episodes). A Material 3 FAB in the bottom-right adds feeds.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PodcastsScreen(
    onFeedClick: (PodcastFeed) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
    showTitle: Boolean = true,
    viewModel: PodcastsViewModel = viewModel(),
) {
    val feeds by viewModel.feeds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val addingState by viewModel.addingState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var feedMenuFor by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            if (showTitle) {
                Text(
                    text = stringResource(R.string.podcasts_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            when {
                isLoading && feeds.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                feeds.isEmpty() -> {
                    EmptyPodcastsMessage(
                        onAddClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    var sort by remember { mutableStateOf(PodcastSort.Name) }
                    val sortedFeeds = remember(feeds, sort) {
                        when (sort) {
                            PodcastSort.Name -> feeds.sortedBy { it.title.lowercase() }
                            PodcastSort.MostEpisodes -> feeds.sortedWith(
                                compareByDescending<PodcastFeed> {
                                    viewModel.episodeCount(it.id)
                                }.thenBy { it.title.lowercase() }
                            )
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = contentPadding.calculateBottomPadding() + 16.dp
                        ),
                    ) {
                        item(key = "podcast_header") {
                            PodcastSortHeader(
                                feedCount = feeds.size,
                                sort = sort,
                                onSortChange = { sort = it },
                            )
                        }
                        items(sortedFeeds, key = { it.id }) { feed ->
                            val count = viewModel.episodeCount(feed.id)
                            FeedRow(
                                feed = feed,
                                episodeCount = count,
                                onClick = { onFeedClick(feed) },
                                onLongPress = { feedMenuFor = feed.id },
                            )
                            if (feedMenuFor == feed.id) {
                                DropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { feedMenuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.podcast_refresh)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                        },
                                        onClick = {
                                            feedMenuFor = null
                                            viewModel.refreshFeed(feed.id)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.podcast_remove)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        },
                                        onClick = {
                                            feedMenuFor = null
                                            viewModel.removeFeed(feed.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Material 3 FAB, bottom-right, for adding feeds.
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.podcast_add),
            )
        }
    }

    if (showAddDialog) {
        AddPodcastDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url ->
                showAddDialog = false
                viewModel.addFeed(url)
            },
        )
    }

    (addingState as? PodcastsViewModel.AddFeedState.Error)?.let { errorState ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeAddFeedState() },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeAddFeedState() }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.podcast_error_title)) },
            text = { Text(errorState.message) },
        )
    }
}

@Composable
private fun FeedRow(
    feed: PodcastFeed,
    episodeCount: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (feed.artworkUrl != null) {
            AsyncImage(
                model = feed.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feed.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (episodeCount > 0) {
                    stringResource(R.string.podcast_episode_count, episodeCount)
                } else {
                    feed.description?.take(80) ?: ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyPodcastsMessage(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = stringResource(R.string.podcasts_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.padding(4.dp))
        Text(
            text = stringResource(R.string.podcasts_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.padding(16.dp))
        TextButton(onClick = onAddClick) {
            Text(stringResource(R.string.podcast_add))
        }
    }
}

@Composable
private fun AddPodcastDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.podcast_add_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.podcast_add_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.podcast_add_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim()) },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.podcast_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/** Sort orders for the Podcasts tab. */
private enum class PodcastSort {
    Name, MostEpisodes
}

/** The count and sort button above the podcast list, matching the Albums tab. */
@Composable
private fun PodcastSortHeader(
    feedCount: Int,
    sort: PodcastSort,
    onSortChange: (PodcastSort) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$feedCount podcasts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        androidx.compose.foundation.layout.Box {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { open = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.SwapVert,
                    contentDescription = "Sort podcasts",
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("Name") },
                    onClick = { onSortChange(PodcastSort.Name); open = false },
                    leadingIcon = {
                        if (sort == PodcastSort.Name) {
                            Icon(
                                androidx.compose.material.icons.Icons.Rounded.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Most episodes") },
                    onClick = { onSortChange(PodcastSort.MostEpisodes); open = false },
                    leadingIcon = {
                        if (sort == PodcastSort.MostEpisodes) {
                            Icon(
                                androidx.compose.material.icons.Icons.Rounded.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }
}
