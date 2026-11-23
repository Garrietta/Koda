package com.ivor.ivormusic.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.rememberArtworkSeed
import com.ivor.ivormusic.ui.theme.vividBlockOf

/*
 * The unfolded Home's Now playing panel: a full-height tile down the right
 * quarter of the screen, always there, in the playing cover's own vivid
 * color. It holds the cover, the title, the progress, previous / play /
 * next, and the queue of what comes up next, each row a tap away. It takes
 * the place of the now-playing bar there, so Home shows no bar when
 * unfolded. With nothing playing it offers the DJ and Liked instead.
 */

/** How long the panel takes to turn to a new cover's color. */
private const val PANEL_FADE_MILLIS = 450

/** The rows of a queue after the one playing, [currentId]; the whole queue when it is not found. */
internal fun upNextOf(queue: List<MusicQueueItem>, currentId: String?): List<MusicQueueItem> {
    val index = queue.indexOfFirst { it.id == currentId }
    return if (index < 0) queue else queue.drop(index + 1)
}

/** The panel bound to the player: what plays, how far along, and the queue after it. */
@Composable
internal fun NowPlayingPanelFor(
    viewModel: PlayerViewModel,
    onOpenPlayer: () -> Unit,
    onStartDj: () -> Unit,
    onOpenLiked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val queue by viewModel.currentQueue.collectAsState()
    val currentId by viewModel.currentQueueItemId.collectAsState()
    // Read in the draw phase only, so the second-by-second progress redraws
    // the bar without recomposing the panel.
    val progress = viewModel.progress.collectAsState()
    val duration = viewModel.duration.collectAsState()
    val upNext = remember(queue, currentId) { upNextOf(queue, currentId) }
    NowPlayingPanel(
        song = song,
        isPlaying = isPlaying,
        upNext = upNext,
        progress = {
            val total = duration.value
            if (total > 0L) (progress.value.toFloat() / total).coerceIn(0f, 1f) else 0f
        },
        onToggle = viewModel::togglePlayPause,
        onNext = viewModel::skipToNext,
        onPrevious = viewModel::skipToPrevious,
        onPlayQueueItem = { id -> viewModel.skipToQueueItem(id) },
        onOpenPlayer = onOpenPlayer,
        onStartDj = onStartDj,
        onOpenLiked = onOpenLiked,
        modifier = modifier,
    )
}

@Composable
internal fun NowPlayingPanel(
    song: Song?,
    isPlaying: Boolean,
    upNext: List<MusicQueueItem>,
    progress: () -> Float,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayQueueItem: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onStartDj: () -> Unit,
    onOpenLiked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val art = song?.let { it.albumArtUri?.toString() ?: it.highResThumbnailUrl ?: it.thumbnailUrl }
    val seed = rememberArtworkSeed(enabled = art != null, albumArtUri = art)
    val block = seed?.let { vividBlockOf(it) }
    val ground by animateColorAsState(
        block?.container ?: if (song != null) scheme.primaryContainer else scheme.surfaceContainer,
        tween(PANEL_FADE_MILLIS),
        label = "panelGround",
    )
    val ink by animateColorAsState(
        block?.onContainer ?: if (song != null) scheme.onPrimaryContainer else scheme.onSurface,
        tween(PANEL_FADE_MILLIS),
        label = "panelInk",
    )
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 12.dp))
            .background(ground),
    ) {
        if (song == null) {
            NothingPlaying(ink, ground, onStartDj, onOpenLiked)
        } else {
            // The cover as wide as the panel but under a fifth of its height,
            // and the words and buttons compact, so the queue has the room.
            val cover = minOf(maxWidth - 32.dp, maxHeight * 0.19f).coerceAtLeast(0.dp)
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Box(
                    Modifier
                        .size(cover)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(onClickLabel = song.title, role = Role.Button, onClick = onOpenPlayer),
                ) {
                    BlockCover(song)
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.clickable(onClickLabel = song.title, role = Role.Button, onClick = onOpenPlayer)) {
                    Text(
                        stringResource(R.string.blocks_now_playing),
                        style = MaterialTheme.typography.labelMedium,
                        color = ink.copy(alpha = 0.8f),
                    )
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = ink.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                // How far along, drawn rather than composed.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .drawBehind {
                            val radius = CornerRadius(size.height / 2f)
                            drawRoundRect(ink.copy(alpha = 0.22f), cornerRadius = radius)
                            drawRoundRect(ink, size = Size(size.width * progress(), size.height), cornerRadius = radius)
                        },
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tonal = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = lerp(ground, ink, 0.16f),
                        contentColor = ink,
                    )
                    FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(40.dp), colors = tonal) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.widget_action_previous))
                    }
                    FilledIconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = ink, contentColor = ground),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.widget_action_pause else R.string.widget_action_play),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(40.dp), colors = tonal) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.widget_action_next))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.widget_up_next),
                    style = MaterialTheme.typography.titleSmall,
                    color = ink,
                )
                Spacer(Modifier.height(4.dp))
                if (upNext.isEmpty()) {
                    Text(
                        stringResource(R.string.ps_queue_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink.copy(alpha = 0.75f),
                    )
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(upNext, key = { it.id }) { item ->
                            QueueRow(item.song, ink) { onPlayQueueItem(item.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(song: Song, ink: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = song.title, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) { BlockCover(song) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The panel with nothing playing: the two quickest ways to start something. */
@Composable
private fun NothingPlaying(ink: Color, ground: Color, onStartDj: () -> Unit, onOpenLiked: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(lerp(ground, ink, 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = ink, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.widget_nothing_playing), style = MaterialTheme.typography.titleMedium, color = ink)
        Spacer(Modifier.height(20.dp))
        val colors = ButtonDefaults.filledTonalButtonColors(containerColor = lerp(ground, ink, 0.12f), contentColor = ink)
        FilledTonalButton(onClick = onStartDj, colors = colors, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.widget_start_dj))
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onOpenLiked, colors = colors, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.widget_start_liked))
        }
    }
}
