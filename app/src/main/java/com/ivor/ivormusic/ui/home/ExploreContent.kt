package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song

private enum class ExploreFilter {
    All, Liked, Downloads, Recent
}

/**
 * The Explore tab: a place to discover what's in the library, separate from
 * Search. Quick Picks filtered by source, plus an Artist Spotlight for the
 * most-played artist.
 */
@Composable
fun ExploreContent(
    songs: List<Song>,
    likedSongs: List<Song>,
    downloadedSongs: List<Song>,
    recentlyPlayed: List<Song>,
    topArtists: List<TopArtist>,
    artistPhotos: Map<String, String>,
    onPlaySongs: (List<Song>, Song?) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    onArtistClick: (String) -> Unit,
    onArtistRadio: (String) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
) {
    var filter by rememberSaveable { mutableStateOf(ExploreFilter.All) }

    val quickPicks = remember(filter, songs, likedSongs, downloadedSongs, recentlyPlayed) {
        when (filter) {
            ExploreFilter.All -> songs
            ExploreFilter.Liked -> likedSongs
            ExploreFilter.Downloads -> downloadedSongs
            ExploreFilter.Recent -> recentlyPlayed
        }.distinctBy { it.id }.take(24)
    }

    val spotlightArtist = remember(topArtists) { topArtists.firstOrNull() }
    val spotlightSongs = remember(spotlightArtist, songs, likedSongs) {
        val name = spotlightArtist?.name ?: return@remember emptyList()
        (songs + likedSongs).distinctBy { it.id }
            .filter { it.artist.equals(name, ignoreCase = true) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "explore_header") {
            Text(
                stringResource(R.string.tab_explore),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        item(key = "explore_chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ExploreFilter.entries) { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = {
                            Text(
                                when (entry) {
                                    ExploreFilter.All -> stringResource(R.string.explore_filter_all)
                                    ExploreFilter.Liked -> stringResource(R.string.explore_filter_liked)
                                    ExploreFilter.Downloads -> stringResource(R.string.explore_filter_downloads)
                                    ExploreFilter.Recent -> stringResource(R.string.explore_filter_recent)
                                }
                            )
                        },
                    )
                }
            }
        }

        if (quickPicks.isNotEmpty()) {
            item(key = "quick_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.explore_quick_picks),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "quick_picks") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(quickPicks, key = { it.id }) { song ->
                        ExplorePickCard(
                            song = song,
                            onClick = { onPlaySongs(quickPicks, song) },
                        )
                    }
                }
            }
        }

        if (spotlightArtist != null) {
            item(key = "spotlight") {
                val photo = artistPhotos[spotlightArtist.name]
                    ?: spotlightArtist.fallbackArtwork
                val topTrack = spotlightSongs.firstOrNull()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.explore_artist_spotlight),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (photo != null) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    spotlightArtist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (topTrack != null) {
                                    Text(
                                        stringResource(
                                            R.string.explore_top_track,
                                            topTrack.title,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onArtistRadio(spotlightArtist.name) },
                                enabled = spotlightSongs.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.explore_artist_radio))
                            }
                            OutlinedButton(
                                onClick = { onArtistClick(spotlightArtist.name) },
                            ) {
                                Text(stringResource(R.string.explore_view_artist))
                            }
                        }
                    }
                }
            }
        }

        if (quickPicks.isEmpty() && spotlightArtist == null) {
            item(key = "explore_empty") {
                Text(
                    stringResource(R.string.explore_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun ExplorePickCard(
    song: Song,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        val art = song.albumArtUri ?: song.highResThumbnailUrl ?: song.thumbnailUrl
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                modifier = Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
