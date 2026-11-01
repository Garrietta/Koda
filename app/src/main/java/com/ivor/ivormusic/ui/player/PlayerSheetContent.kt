package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.media3.common.Player
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.data.queuePlaylistDateText
import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.ui.components.QueueDragHandle
import com.ivor.ivormusic.ui.components.QueueRowContainer
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.components.queueDragLongPress
import com.ivor.ivormusic.ui.components.rememberFocusedQueueListState
import com.ivor.ivormusic.ui.components.rememberQueueRemoval
import com.ivor.ivormusic.ui.components.rememberQueueReorderState
import com.ivor.ivormusic.ui.theme.MontserratFamily
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.data.MusicQueueItem

/**
 * I do not fucking ned that many slop comments thank you
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveNowPlayingView(
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    progress: Long,
    duration: Long,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    onFavoriteToggle: (Boolean) -> Unit,
    onDownloadToggle: () -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isLocalOriginal: Boolean,
    lyricsResult: LyricsResult,
    onSeekTo: (Long) -> Unit,
    ambientBackground: Boolean,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    onShowOptions: () -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: PlayerViewModel,
    primaryColor: Color,
    primaryContainerColor: Color,
    secondaryContainerColor: Color,
    tertiaryContainerColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    stableShapes: IconButtonShapes
) {
    val isDjMixPlaying by viewModel.isDjMixPlaying.collectAsState()
    // State for toggling between album art and lyrics
    var showLyrics by remember { mutableStateOf(false) }
    val playerHaptics = rememberPlayerHaptics()

    // Swipe-to-skip, shared by the album art and the title/artist block so
    // both commit at the same threshold with the same spring home.
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { playerHaptics.skip(); viewModel.skipToNext() },
        onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() }
    )
    
    // Get album art URL for background
    val albumArtUrl = currentSong?.highResThumbnailUrl 
        ?: currentSong?.thumbnailUrl 
        ?: currentSong?.albumArtUri?.toString()
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Chromatic Mist ambient background
        ChromaticMistBackground(
            albumArtUrl = albumArtUrl,
            enabled = ambientBackground,
            modifier = Modifier.fillMaxSize()
        )
        
        // Content layer
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
        // ========== 1. TOP BAR ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .statusBarsPadding()
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapse button - static shape (no morphing needed for utility buttons)
            FilledIconButton(
                onClick = onCollapse,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(28.dp))
            }
            
            // Expressive center toggle - morphs between "Now Playing" and "Lyrics" with animation
            val pillWidth by animateFloatAsState(
                targetValue = if (showLyrics) 140f else 160f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "PillWidth"
            )
            
            Surface(
                modifier = Modifier
                    .width(pillWidth.dp)
                    .clickable { showLyrics = !showLyrics },
                shape = RoundedCornerShape(24.dp),
                color = if (showLyrics) MaterialTheme.colorScheme.surfaceContainerHigh else primaryColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Crossfade(targetState = showLyrics, label = "PillIcon") { isLyrics ->
                        Icon(
                            imageVector = if (isLyrics) Icons.AutoMirrored.Filled.QueueMusic else Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = if (isLyrics) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Crossfade(targetState = showLyrics, label = "PillText") { isLyrics ->
                        Text(
                            text = if (isLyrics) stringResource(R.string.ps_now_playing) else stringResource(R.string.ps_lyrics),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isLyrics) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            // Right Side Group: Queue, then the overflow menu last
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Queue button
                FilledIconButton(
                    onClick = onShowQueue,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", modifier = Modifier.size(24.dp))
                }

                FilledIconButton(
                    onClick = onShowOptions,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.MoreVert, "More options", modifier = Modifier.size(24.dp))
                }
            }
        }
        
        // ========== 2. ALBUM ART / LYRICS (Fills available space) ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // Off while lyrics are up: that view scrolls and seeks.
                .swipeToSkip(swipeToSkip, enabled = !showLyrics),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = showLyrics, label = "AlbumLyricsCrossfade") { isLyricsVisible ->
                if (isLyricsVisible) {
                    // Synced Lyrics View
                    SyncedLyricsView(
                        lyricsResult = lyricsResult,
                        currentPositionMs = progress,
                        isPlaying = isPlaying,
                        onSeekTo = onSeekTo,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                        onSurfaceVariantColor = onSurfaceVariantColor
                    )
                } else {
                    // Album Art
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Guard against invalid dimensions during transitions
                        // Use minimum usable size (not just zero) for high DPI compatibility
                        if (maxWidth < 50.dp || maxHeight < 50.dp) {
                            return@BoxWithConstraints
                        }
                        
                        val albumSize = minOf(maxWidth, maxHeight) * 0.9f
                        val cornerRadius = albumSize * 0.15f
                        
                        Box(contentAlignment = Alignment.Center) {
                            // Main album art container with expressive squircle shape
                            val styleWheel = LocalPlayerStyleWheelController.current
                            Surface(
                                modifier = Modifier
                                    .size(albumSize)
                                    .swipeToSkipFollow(swipeToSkip, SwipeToSkipDefaults.ArtFollow)
                                    .styleWheelHold(styleWheel),
                                shape = RoundedCornerShape(cornerRadius),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Album Art
                                    val artSong = currentSong?.takeIf { it.albumArtUri != null || it.thumbnailUrl != null }
                                    if (artSong != null) {
                                        PlayerArtwork(
                                            song = artSong,
                                            contentDescription = stringResource(R.string.cd_album_art),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(cornerRadius)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        // Placeholder with expressive shape
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            primaryContainerColor.copy(alpha = 0.5f),
                                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                modifier = Modifier.size(albumSize * 0.35f),
                                                tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                    
                                    // Subtle inner gradient for depth
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(cornerRadius))
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.1f)
                                                    ),
                                                    startY = 0f,
                                                    endY = Float.POSITIVE_INFINITY
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ========== 3. CONTROLS & INFO (Bottom Section) ==========
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🌟 Wavy Progress Indicator (Expressive!)
            // While the user is scrubbing we only track the finger locally and
            // issue a single seek on release — seeking on every drag frame causes
            // rebuffering storms on streamed tracks.
            var scrubPosition by remember { mutableStateOf<Float?>(null) }
            val displayedProgress = scrubPosition?.toLong() ?: progress
            val scrubReturnPoint = LocalScrubReturnPoint.current
            // The position advances every frame from the clock rather than a
            // spring chasing a once-a-second sample, which is what made the
            // thumb step. See rememberSmoothProgress.
            val smoothProgress = rememberSmoothProgress(
                positionMs = progress,
                durationMs = duration,
                isPlaying = isPlaying,
                scrubPositionMs = scrubPosition
            )
            Box(contentAlignment = Alignment.Center) {
                val thickStroke = Stroke(width = with(LocalDensity.current) { 6.dp.toPx() }, cap = StrokeCap.Round)

                ExpressiveScrubber(
                    progress = { smoothProgress.value },
                    modifier = Modifier.fillMaxWidth().height(scrubberTrackHeight(14.dp)),
                    stroke = thickStroke,
                    trackStroke = thickStroke,
                    color = primaryColor,
                    trackColor = onSurfaceVariantColor.copy(alpha = 0.15f)
                )

                // Invisible slider for touch interaction
                Slider(
                    interactionSource = LocalPlayerScrubInteraction.current,
                    value = scrubPosition ?: progress.toFloat(),
                    // Through the return point: the drag leaves a marker where it
                    // started and snaps back onto it, and letting go on the marker
                    // is "never mind" rather than a seek. See ScrubReturnPoint.
                    onValueChange = {
                        scrubPosition = scrubReturnPoint.follow(
                            value = it,
                            rangeEnd = duration.toFloat().coerceAtLeast(1f)
                        ) { smoothProgress.value }
                    },
                    onValueChangeFinished = {
                        val cancelled = scrubReturnPoint.release()
                        if (!cancelled) scrubPosition?.let { onSeekTo(it.toLong()) }
                        scrubPosition = null
                    },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Time Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(displayedProgress), style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor)
                Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor)
            }
            
            PlayerVisualizerSlot(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(12.dp))

            // Song Title & Artist
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .swipeToSkip(swipeToSkip)
                    .swipeToSkipFollow(swipeToSkip),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isDjMixPlaying) {
                    AiDjBadge()
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = currentSong?.title?.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled_song),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onSurfaceColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Clickable artist name
                val artistName = currentSong?.artist?.takeIf { !isUnknownArtist(it) } ?: "Unknown Artist"
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryColor.copy(alpha = 0.9f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            enabled = artistName != "Unknown Artist"
                        ) { onArtistClick(artistName) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ========== EXPRESSIVE STANDARD BUTTON GROUP ==========
            // Using Standard ButtonGroup for squishy physics - buttons expand on press
            // and neighbors compress to accommodate
            // material3 alpha24 turned ButtonGroup's content into a non-composable builder
            // (customItem/clickableItem/toggleableItem), so the interaction sources have to be
            // remembered out here rather than inline per button.
            val shuffleInteraction = remember { MutableInteractionSource() }
            val prevInteraction = remember { MutableInteractionSource() }
            val playInteraction = remember { MutableInteractionSource() }
            val nextInteraction = remember { MutableInteractionSource() }
            val repeatInteraction = remember { MutableInteractionSource() }

            ButtonGroup(
                // The five transport buttons are weighted to fill the row exactly, so the
                // group can never overflow. alpha24 dropped the overload without this
                // parameter, so it is supplied empty rather than rendering a menu.
                overflowIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp) // Minor spacing between buttons
            ) {
                // Shuffle Toggle - with animateWidth for squishy physics
                customItem(
                    buttonGroupContent = {
                        // DJ mixes are strictly ordered: no shuffle option.
                        if (!isDjMixPlaying) {
                            FilledTonalIconToggleButton(
                                checked = shuffleModeEnabled,
                                onCheckedChange = { viewModel.toggleShuffle() },
                                interactionSource = shuffleInteraction,
                                modifier = Modifier
                                    .weight(0.8f)
                                    .fillMaxHeight()
                                    .animateWidth(shuffleInteraction)
                            ) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    contentDescription = stringResource(R.string.cd_shuffle),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    },
                    menuContent = {}
                )

                // Previous Button - with animateWidth for squishy physics
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconButton(
                            onClick = { playerHaptics.skip(); viewModel.skipToPrevious() },
                            shapes = stableShapes,
                            interactionSource = prevInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .animateWidth(prevInteraction)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = stringResource(R.string.cd_previous),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    },
                    menuContent = {}
                )

                // 🌟 Play/Pause Button (center, larger weight for emphasis)
                customItem(
                    buttonGroupContent = {
                        FilledIconButton(
                            onClick = {
                                playerHaptics.playPause(!viewModel.isPlaying.value)
                                viewModel.togglePlayPause()
                            },
                            shapes = stableShapes,
                            interactionSource = playInteraction,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = primaryContainerColor,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxHeight()
                                .animateWidth(playInteraction)
                        ) {
                            // FIX: Only show loading if we are NOT playing. If audio is playing, always show Pause.
                            if (isBuffering && playWhenReady && !isPlaying) {
                                // 🌟 Organic morphing loading with MaterialShapes
                                LoadingIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    polygons = listOf(
                                        MaterialShapes.SoftBurst,
                                        MaterialShapes.Cookie9Sided,
                                        MaterialShapes.Pill,
                                        MaterialShapes.Sunny
                                    )
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    },
                    menuContent = {}
                )

                // Next Button - with animateWidth for squishy physics
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconButton(
                            onClick = { playerHaptics.skip(); viewModel.skipToNext() },
                            shapes = stableShapes,
                            interactionSource = nextInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .animateWidth(nextInteraction)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.cd_next),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    },
                    menuContent = {}
                )

                // Repeat Toggle - with animateWidth for squishy physics
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconToggleButton(
                            checked = repeatMode != Player.REPEAT_MODE_OFF,
                            onCheckedChange = { viewModel.toggleRepeat() },
                            interactionSource = repeatInteraction,
                            modifier = Modifier
                                .weight(0.8f)
                                .fillMaxHeight()
                                .animateWidth(repeatInteraction)
                        ) {
                            Icon(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = stringResource(R.string.cd_repeat),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                    menuContent = {}
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 🌟 Favorite / Download / Sleep Timer - connected button group
            val sleepTimer = rememberSleepTimerControl(viewModel = viewModel)
            val sleepTimerActive = sleepTimer.active
            val groupButtonColors = ToggleButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = onSurfaceVariantColor
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                // Favorite (bursts on like)
                ToggleButton(
                    checked = isFavorite,
                    onCheckedChange = onFavoriteToggle,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    colors = groupButtonColors
                ) {
                    com.ivor.ivormusic.ui.components.LikeBurstIcon(
                        isFavorite = isFavorite,
                        iconSize = 22.dp
                    )
                }

                // Download (or a local-file indicator for device songs)
                ToggleButton(
                    checked = isDownloaded || isDownloading,
                    onCheckedChange = { if (!isDownloading) onDownloadToggle() },
                    enabled = !isLocalOriginal,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                    colors = groupButtonColors
                ) {
                    when {
                        isLocalOriginal -> Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = stringResource(R.string.cd_local_file),
                            modifier = Modifier.size(22.dp)
                        )
                        isDownloading -> CircularWavyProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = LocalContentColor.current
                        )
                        else -> Icon(
                            imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                            contentDescription = if (isDownloaded) stringResource(R.string.song_options_downloaded) else stringResource(R.string.song_options_download),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Sleep timer (checked while a timer is running; tap opens the dialog)
                ToggleButton(
                    checked = sleepTimerActive,
                    onCheckedChange = { sleepTimer.open() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    colors = groupButtonColors
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = stringResource(R.string.sleep_timer_title),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

        }
        }
    } // End Box wrapper
}

/**
 * The Classic player, in Material 3 Expressive terms: two hero moments - the
 * cover, whose shape is the play state, and the play button, the one wide
 * filled control in a standard button group - and everything else in tonal
 * containers that change shape when pressed or turned on.
 *
 * Below the controls, Up next, Lyrics and Related open a panel that rises over
 * the player. Its open flag is [rememberPlayerQueueOpen], shared with
 * [ExpandablePlayer], so back closes the panel and returns to the player, the
 * same as every other style's queue.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSheetContent(
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    /** Hand this song to the video player; null where there is no video pipeline. */
    onWatchAsVideo: (() -> Unit)? = null,
    onAlbumClick: (String) -> Unit = {},
    onOpenAlbum: (PlaylistDisplayItem) -> Unit = {}
) {
    // Back is handled by ExpandablePlayer: it collapses the player, or closes
    // this style's panel first while the panel is open.
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isDjMixPlaying by viewModel.isDjMixPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val currentQueueItemId by viewModel.currentQueueItemId.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    val playLabel = stringResource(R.string.cd_play)
    val pauseLabel = stringResource(R.string.cd_pause)
    
    // Download states
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    
    // Lyrics state
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    
    var showQueue by remember { mutableStateOf(false) }

    // Stable shapes - prevents "square flash" on initial render
    val stableIconButtonShapes = IconButtonDefaults.shapes()

    // Theme colors
    val onSurfaceColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    var showOptions by rememberNowPlayingOptionsOpen()



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Crossfade(targetState = showQueue, label = "PlayerQueueTransition") { isQueueVisible ->
            if (isQueueVisible) {
                // Determine loading state for the specific "load more" action
                val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                
                ExpressiveQueueView(
                        isDjMixPlaying = isDjMixPlaying,
                    queue = currentQueue,
                    currentQueueItemId = currentQueueItemId,
                    onQueueItemClick = { item -> viewModel.skipToQueueItem(item.id) },
                    onMoveSong = { from, to -> viewModel.moveQueueItem(from, to) },
                    onUndoRemove = { viewModel.undoQueueRemoval() },
                    onRemoveItem = { item -> viewModel.removeQueueItem(item.id) },
                    onLoadMore = onLoadMore,
                    isLoadingMore = isLoadingMore,
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    onSaveQueue = { name, description, onSaved ->
                        viewModel.saveQueueAsPlaylist(name, description, onSaved)
                    },
                    // Download status pass-through
                    isDownloaded = { id -> viewModel.isDownloaded(id) },
                    isDownloading = { id -> viewModel.isDownloading(id) },
                    isLocalOriginal = { song -> viewModel.isLocalOriginal(song) },
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor,
                    stableShapes = stableIconButtonShapes
                )
            } else {
                ExpressiveNowPlayingView(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playWhenReady = playWhenReady,
                    progress = progress,
                    duration = duration,
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    isFavorite = isFavorite,
                    onFavoriteToggle = { viewModel.toggleCurrentSongLike() },
                    // Download actions
                    onDownloadToggle = { currentSong?.let { viewModel.toggleDownload(it) } },
                    isDownloaded = currentSong?.let { viewModel.isDownloaded(it.id) } ?: false,
                    isDownloading = currentSong?.let { viewModel.isDownloading(it.id) } ?: false,
                    isLocalOriginal = currentSong?.let { viewModel.isLocalOriginal(it) } ?: false,
                    // Lyrics
                    lyricsResult = lyricsResult,
                    onSeekTo = { viewModel.seekTo(it) },
                    ambientBackground = ambientBackground,
                    
                    onCollapse = onCollapse,
                    onShowQueue = { showQueue = true },
                    onShowOptions = { showOptions = true },
                    onArtistClick = onArtistClick,
                    viewModel = viewModel,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    secondaryContainerColor = secondaryContainerColor,
                    tertiaryContainerColor = tertiaryContainerColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor,
                    stableShapes = stableIconButtonShapes
                )
            }
        }

    }

    // The overflow menu, gated on a song so it can never open against nothing.
    if (showOptions) {
        currentSong?.let { song ->
            NowPlayingOptionsSheet(
                song = song,
                viewModel = viewModel,
                onDismiss = { showOptions = false },
                onArtistClick = onArtistClick,
                onWatchAsVideo = onWatchAsVideo,
                onAlbumClick = onAlbumClick,
                onOpenAlbum = onOpenAlbum
            )
        }
    }
}

/** The panel's three tabs, in the order YouTube Music has them. Not persisted. */
private enum class ClassicTab(val label: Int, val icon: ImageVector) {
    UpNext(R.string.cl_tab_up_next, Icons.AutoMirrored.Rounded.QueueMusic),
    Lyrics(R.string.cl_tab_lyrics, Icons.Rounded.Lyrics),
    Related(R.string.cl_tab_related, Icons.Rounded.Explore)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ClassicNowPlaying(
    viewModel: PlayerViewModel,
    currentSong: Song?,
    onCollapse: () -> Unit,
    onShowOptions: () -> Unit,
    onArtistClick: (String) -> Unit,
    onOpenTab: (ClassicTab) -> Unit,
    onDragPanel: (Float) -> Unit,
    onSettlePanel: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleOn by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    // Collected so the download button follows the download as it runs.
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val haptics = rememberPlayerHaptics()
    val sleepTimer = rememberSleepTimerControl(viewModel = viewModel)
    // Playing or about to: the cover and the play button both read this.
    val active = isPlaying || (isBuffering && playWhenReady)

    val swipeToSkip = rememberSwipeToSkip(
        onNext = { haptics.skip(); viewModel.skipToNext() },
        onPrevious = { haptics.skip(); viewModel.skipToPrevious() }
    )
    val tonal = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ---- Top bar. The title is centred on the bar itself, not between
        // the buttons, so one button on the left and two on the right cannot
        // push it off centre.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 16.dp)
        ) {
            FilledTonalIconButton(
                onClick = onCollapse,
                shapes = IconButtonDefaults.shapes(),
                colors = tonal,
                modifier = Modifier.align(Alignment.CenterStart).size(48.dp)
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cl_collapse),
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 112.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.ps_now_playing).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.18.em,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                val album = currentSong?.album?.takeIf { it.isNotBlank() }
                if (album != null) {
                    Text(
                        text = album,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // A toggle, so a running timer is visible as a filled, squared
                // button rather than only a tint.
                FilledTonalIconToggleButton(
                    checked = sleepTimer.active,
                    onCheckedChange = { sleepTimer.open() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = stringResource(R.string.sleep_timer_title))
                }
                FilledTonalIconButton(
                    onClick = onShowOptions,
                    shapes = IconButtonDefaults.shapes(),
                    colors = tonal,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                }
            }
        }

        // ---- Hero one: the cover. Its shape is the play state - a soft
        // square while the music plays, a twelve-sided cookie that sinks back
        // when it stops - so pause reads at a glance from across a room.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .swipeToSkip(swipeToSkip),
            contentAlignment = Alignment.Center
        ) {
            if (maxWidth < 64.dp || maxHeight < 64.dp) return@BoxWithConstraints
            val coverSize = minOf(maxWidth - 40.dp, maxHeight - 12.dp)
            val pausedness by animateFloatAsState(
                targetValue = if (active) 0f else 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
                label = "classicCoverMorph"
            )
            val coverMorph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie12Sided) }
            val coverShape = remember(pausedness) { UnitMorphShape(coverMorph, pausedness.coerceIn(0f, 1f)) }
            val styleWheel = LocalPlayerStyleWheelController.current
            Surface(
                modifier = Modifier
                    .size(coverSize)
                    .graphicsLayer {
                        val scale = 1f - 0.1f * pausedness
                        scaleX = scale
                        scaleY = scale
                    }
                    .swipeToSkipFollow(swipeToSkip, SwipeToSkipDefaults.ArtFollow)
                    .styleWheelHold(styleWheel),
                shape = coverShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 12.dp
            ) {
                val artSong = currentSong?.takeIf { it.albumArtUri != null || it.thumbnailUrl != null }
                if (artSong != null) {
                    PlayerArtwork(
                        song = artSong,
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(coverSize * 0.32f),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            // ---- The song: the poster face at size, the artist in the accent,
            // and the two things done to a song as tonal toggles.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .swipeToSkip(swipeToSkip)
                        .swipeToSkipFollow(swipeToSkip)
                ) {
                    Text(
                        text = currentSong?.title?.takeIf { !isUnknownTitle(it) }
                            ?: stringResource(R.string.untitled_song),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.02).em
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        // A long title travels rather than being cut.
                        modifier = Modifier.basicMarquee(initialDelayMillis = 2000)
                    )
                    val artist = currentSong?.artist?.takeIf { !isUnknownArtist(it) }
                    Text(
                        text = artist ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = artist != null) { artist?.let(onArtistClick) }
                    )
                }
                val song = currentSong
                if (song != null) {
                    val isLocal = viewModel.isLocalOriginal(song)
                    val isDownloading = song.id in downloadingIds
                    val isDownloaded = downloadedSongs.any { it.id == song.id }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconToggleButton(
                        checked = isDownloaded || isDownloading || isLocal,
                        onCheckedChange = { if (!isDownloading && !isLocal) viewModel.toggleDownload(song) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        when {
                            isLocal -> Icon(Icons.Rounded.Smartphone, contentDescription = stringResource(R.string.cd_local_file))
                            isDownloading -> CircularWavyProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = LocalContentColor.current
                            )
                            isDownloaded -> Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.song_options_downloaded)
                            )
                            else -> Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.song_options_download))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconToggleButton(
                        checked = isFavorite,
                        onCheckedChange = { viewModel.toggleCurrentSongLike() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        LikeBurstIcon(isFavorite = isFavorite, iconSize = 24.dp)
                    }
                }
            }

            // ---- The seek bar. The Slider owns the drag; the scrubber draws.
            var scrubPosition by remember { mutableStateOf<Float?>(null) }
            val displayedProgress = scrubPosition?.toLong() ?: progress
            val scrubReturnPoint = LocalScrubReturnPoint.current
            val smoothProgress = rememberSmoothProgress(
                positionMs = progress,
                durationMs = duration,
                isPlaying = isPlaying,
                scrubPositionMs = scrubPosition
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = 14.dp)) {
                val stroke = Stroke(width = with(LocalDensity.current) { 7.dp.toPx() }, cap = StrokeCap.Round)
                ExpressiveScrubber(
                    progress = { smoothProgress.value },
                    modifier = Modifier.fillMaxWidth().height(scrubberTrackHeight(14.dp)),
                    stroke = stroke,
                    trackStroke = stroke,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
                Slider(
                    interactionSource = LocalPlayerScrubInteraction.current,
                    value = scrubPosition ?: progress.toFloat(),
                    onValueChange = {
                        scrubPosition = scrubReturnPoint.follow(
                            value = it,
                            rangeEnd = duration.toFloat().coerceAtLeast(1f)
                        ) { smoothProgress.value }
                    },
                    onValueChangeFinished = {
                        val cancelled = scrubReturnPoint.release()
                        if (!cancelled) scrubPosition?.let { viewModel.seekTo(it.toLong()) }
                        scrubPosition = null
                    },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration(displayedProgress),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatDuration(duration),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PlayerVisualizerSlot(modifier = Modifier.fillMaxWidth())

            // ---- Hero two: the transport, a standard button group. A pressed
            // button widens and its neighbours give way; shuffle and repeat
            // square off when on; play is the one wide filled control, a
            // squircle while playing and a full pill while paused.
            val shuffleInteraction = remember { MutableInteractionSource() }
            val prevInteraction = remember { MutableInteractionSource() }
            val playInteraction = remember { MutableInteractionSource() }
            val nextInteraction = remember { MutableInteractionSource() }
            val repeatInteraction = remember { MutableInteractionSource() }
            val playCorner by animateDpAsState(
                targetValue = if (active) 28.dp else 48.dp,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                label = "classicPlayCorner"
            )
            val playShapes = IconButtonShapes(
                shape = RoundedCornerShape(playCorner),
                pressedShape = RoundedCornerShape(16.dp)
            )
            val transportTonal = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconToggleButton(
                            checked = shuffleOn,
                            onCheckedChange = { viewModel.toggleShuffle() },
                            interactionSource = shuffleInteraction,
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight()
                                .animateWidth(shuffleInteraction)
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = stringResource(R.string.cd_shuffle),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    },
                    menuContent = {}
                )
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconButton(
                            onClick = { haptics.skip(); viewModel.skipToPrevious() },
                            shapes = IconButtonDefaults.shapes(),
                            colors = transportTonal,
                            interactionSource = prevInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .animateWidth(prevInteraction)
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.cd_previous),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    },
                    menuContent = {}
                )
                customItem(
                    buttonGroupContent = {
                        FilledIconButton(
                            onClick = {
                                haptics.playPause(!viewModel.isPlaying.value)
                                viewModel.togglePlayPause()
                            },
                            shapes = playShapes,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            interactionSource = playInteraction,
                            modifier = Modifier
                                .weight(1.9f)
                                .fillMaxHeight()
                                .animateWidth(playInteraction)
                        ) {
                            if (isBuffering && playWhenReady && !isPlaying) {
                                LoadingIndicator(
                                    modifier = Modifier.size(44.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    polygons = listOf(
                                        MaterialShapes.SoftBurst,
                                        MaterialShapes.Cookie9Sided,
                                        MaterialShapes.Pill,
                                        MaterialShapes.Sunny
                                    )
                                )
                            } else {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    },
                    menuContent = {}
                )
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconButton(
                            onClick = { haptics.skip(); viewModel.skipToNext() },
                            shapes = IconButtonDefaults.shapes(),
                            colors = transportTonal,
                            interactionSource = nextInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .animateWidth(nextInteraction)
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.cd_next),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    },
                    menuContent = {}
                )
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconToggleButton(
                            checked = repeatMode != Player.REPEAT_MODE_OFF,
                            onCheckedChange = { viewModel.toggleRepeat() },
                            interactionSource = repeatInteraction,
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight()
                                .animateWidth(repeatInteraction)
                        ) {
                            Icon(
                                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = stringResource(R.string.cd_repeat),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    },
                    menuContent = {}
                )
            }
        }

        // ---- The way into the panel: a tonal pill of three labelled tabs,
        // tapped or pulled up.
        ClassicPanelEntry(
            onSelect = onOpenTab,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 12.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { onDragPanel(it) },
                    onDragStopped = { velocity -> onSettlePanel(velocity) }
                )
        )
    }
}

/**
 * Up next, Lyrics and Related, under the controls: one tonal container with a
 * grip, so it reads as something that comes up from the edge, and labels kept
 * beside the icons - an icon alone does not say "Related".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ExpressiveQueueView(
    isDjMixPlaying: Boolean = false,
    queue: List<MusicQueueItem>,
    currentQueueItemId: String?,
    onQueueItemClick: (MusicQueueItem) -> Unit,
    onMoveSong: (fromIndex: Int, toIndex: Int) -> Unit,
    onUndoRemove: () -> Unit,
    onRemoveItem: (MusicQueueItem) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    onCollapse: () -> Unit,
    onBackToPlayer: () -> Unit,
    onSaveQueue: (name: String, description: String?, onSaved: (String, Int) -> Unit) -> Unit,
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    isLocalOriginal: (Song) -> Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    stableShapes: IconButtonShapes
) {
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val currentSong = queue.find { it.id == currentQueueItemId }?.song
    val currentQueueIndex = remember(queue, currentQueueItemId) {
        queue.indexOfFirst { it.id == currentQueueItemId }
    }
    val listState = rememberFocusedQueueListState(
        currentQueueIndex = currentQueueIndex,
        leadingItemCount = 1,
    )
    val rowKeys = remember(queue) { queue.map { it.id } }
    val reorder = rememberQueueReorderState(
        listState = listState,
        keys = rowKeys,
        onMove = onMoveSong,
        onSettle = {},
        dragEnabled = !isDjMixPlaying,
    )
    val removal = rememberQueueRemoval(onUndo = onUndoRemove)
    var showSaveDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ========== 1. TOP BAR (Matching Player Style) ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapse button - static shape
            FilledIconButton(
                onClick = onCollapse,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(28.dp))
            }
            
            // Queue title pill (matching player's "Now Playing" pill)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ps_up_next),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Back to player button - static shape
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledIconButton(
                    onClick = { showSaveDialog = true },
                    enabled = queue.isNotEmpty(),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        stringResource(R.string.queue_save_as_playlist),
                        modifier = Modifier.size(24.dp)
                    )
                }
                FilledIconButton(
                    onClick = onBackToPlayer,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.MusicNote, "Now Playing", modifier = Modifier.size(24.dp))
                }
            }
        }
        
        // ========== 2. MAIN CONTENT ==========
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 120.dp)
                .padding(horizontal = 24.dp)
        ) {
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(), 
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.ps_queue_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurfaceVariantColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ========== FEATURED NOW PLAYING CARD ==========
                    currentSong?.let { song ->
                        item(key = "now_playing_header") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Featured album art (matching player style but smaller)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Main album art container
                                    Surface(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clickable { onBackToPlayer() },
                                        shape = RoundedCornerShape(32.dp),
                                        shadowElevation = 8.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (song.albumArtUri != null || song.thumbnailUrl != null) {
                                                SongArtwork(
                                                    song = song,
                                                    contentDescription = stringResource(R.string.cd_now_playing),
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(32.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    primaryContainerColor.copy(alpha = 0.5f),
                                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                                )
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.MusicNote,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(64.dp),
                                                        tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                                                    )
                                                }
                                            }
                                            
                                            // Gradient overlay for depth
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(32.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                Color.Black.copy(alpha = 0.1f)
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                                
                                // Song info
                                Text(
                                    text = song.title.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled_song),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = onSurfaceColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = song.artist.takeIf { !isUnknownArtist(it) } ?: "Unknown Artist",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onSurfaceVariantColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // Divider with label
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = onSurfaceVariantColor.copy(alpha = 0.1f)
                                    )
                                    Text(
                                        text = stringResource(R.string.ps_queue),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurfaceVariantColor,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = onSurfaceVariantColor.copy(alpha = 0.1f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                    
                    // ========== QUEUE ITEMS ==========
                    itemsIndexed(
                        queue,
                        key = { index, _ -> rowKeys.getOrElse(index) { "queue_$index" } }
                    ) { index, queueItem ->
                        val song = queueItem.song
                        val isCurrent = queueItem.id == currentQueueItemId
                        val key = rowKeys.getOrElse(index) { "queue_$index" }
                        val isDragging = reorder.draggingKey == key

                        QueueRowContainer(
                            isDragging = isDragging,
                            dragOffset = reorder.offsetFor(key),
                            removeEnabled = queue.size > 1,
                            onRemove = {
                                onRemoveItem(queueItem)
                                removal.onRemoved(song.title)
                            },
                            modifier = if (isDragging) Modifier else Modifier.animateItem()
                        ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .queueDragLongPress(reorder, key),
                            shape = RoundedCornerShape(20.dp),
                            color = if (isCurrent) primaryColor.copy(alpha = 0.12f)
                                   else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                primaryColor.copy(alpha = 0.3f)
                            ) else null,
                            onClick = { onQueueItemClick(queueItem) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Queue position
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = stringResource(R.string.cd_playing),
                                            tint = primaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = onSurfaceVariantColor
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                // Thumbnail with expressive rounded corners
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shadowElevation = if (isCurrent) 4.dp else 2.dp
                                ) {
                                    AsyncImage(
                                        model = song.thumbnailUrl ?: song.albumArtUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) primaryColor else onSurfaceColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCurrent) primaryColor.copy(alpha = 0.7f) else onSurfaceVariantColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))

                                // Download Status Icon
                                if (isDownloading(song.id)) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = if (isCurrent) primaryColor else onSurfaceVariantColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else if (isDownloaded(song.id)) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.song_options_downloaded),
                                        tint = if (isCurrent) primaryColor else onSurfaceVariantColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else if (isLocalOriginal(song)) {
                                    Icon(
                                        imageVector = Icons.Rounded.Smartphone,
                                        contentDescription = stringResource(R.string.cd_local_file),
                                        tint = if (isCurrent) primaryColor.copy(alpha=0.7f) else onSurfaceVariantColor.copy(alpha=0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                QueueDragHandle(
                                    state = reorder,
                                    rowKey = key,
                                    tint = onSurfaceVariantColor.copy(alpha = 0.7f)
                                )

                                IconButton(
                                    onClick = {
                                        onRemoveItem(queueItem)
                                        removal.onRemoved(song.title)
                                    },
                                    enabled = queue.size > 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.cd_remove_from_queue),
                                        tint = onSurfaceVariantColor
                                    )
                                }
                            }
                        }
                        }
                    }

                    // Load More Button with expressive shape morphing
                    item(key = "load_more_button") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = onLoadMore,
                                enabled = !isLoadingMore,
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
                            ) {
                                if (isLoadingMore) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        polygons = listOf(
                                            MaterialShapes.Cookie9Sided,
                                            MaterialShapes.Pill,
                                            MaterialShapes.Sunny
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(stringResource(R.string.loading), style = MaterialTheme.typography.titleSmall)
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(stringResource(R.string.load_more), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        DismissibleSnackbarHost(
            hostState = removal.hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )

        if (showSaveDialog) {
            CreatePlaylistDialog(
                initialName = stringResource(
                    R.string.queue_save_default_name,
                    remember { queuePlaylistDateText() }
                ),
                onDismiss = { showSaveDialog = false },
                onCreate = { name, description ->
                    showSaveDialog = false
                    onSaveQueue(name, description) { savedName, trackCount ->
                        removal.announce(
                            context.resources.getQuantityString(
                                R.plurals.queue_saved_to_playlist,
                                trackCount,
                                trackCount,
                                savedName
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ClassicPanelEntry(
    onSelect: (ClassicTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            Row(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp)) {
                ClassicTab.entries.forEach { tab ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelect(tab) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(tab.label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicPanel(
    viewModel: PlayerViewModel,
    currentSong: Song?,
    tab: ClassicTab,
    onTabChange: (ClassicTab) -> Unit,
    onClose: () -> Unit,
    onDragPanel: (Float) -> Unit,
    onSettlePanel: (Float) -> Unit,
    onLoadMore: () -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val removal = rememberQueueRemoval(onUndo = { viewModel.undoQueueRemoval() })
    val haptics = rememberPlayerHaptics()

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // The song, small, at the top of the panel - tap it or pull it
                // down to go back to the player.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { onDragPanel(it) },
                            onDragStopped = { velocity -> onSettlePanel(velocity) }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                    currentSong?.let { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onClose)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongArtwork(
                                song = song,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FilledIconButton(
                                onClick = {
                                    haptics.playPause(!viewModel.isPlaying.value)
                                    viewModel.togglePlayPause()
                                },
                                shapes = IconButtonDefaults.shapes(),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Switching the panel's view is what a connected button group
                // is for: one selected, each shaped by its place in the row.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    val count = ClassicTab.entries.size
                    ClassicTab.entries.forEachIndexed { index, entry ->
                        ToggleButton(
                            checked = entry == tab,
                            onCheckedChange = { onTabChange(entry) },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            colors = ToggleButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Icon(entry.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(entry.label), maxLines = 1)
                        }
                    }
                }

                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.weight(1f),
                    label = "classicPanelTab"
                ) { shown ->
                    when (shown) {
                        ClassicTab.UpNext -> ClassicUpNext(
                            viewModel = viewModel,
                            removal = removal,
                            onLoadMore = onLoadMore
                        )
                        ClassicTab.Lyrics -> {
                            val lyrics by viewModel.lyricsResult.collectAsState()
                            val position by viewModel.progress.collectAsState()
                            SyncedLyricsView(
                                lyricsResult = lyrics,
                                currentPositionMs = position,
                                isPlaying = isPlaying,
                                onSeekTo = { viewModel.seekTo(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ClassicTab.Related -> ClassicRelated(
                            viewModel = viewModel,
                            song = currentSong,
                            onArtistClick = onArtistClick,
                            onAnnounce = { removal.announce(it) }
                        )
                    }
                }
            }
            DismissibleSnackbarHost(
                hostState = removal.hostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Up next: the queue in the order it will be heard, reorderable and
 * swipe-removable through the shared queue machinery, drawn as one grouped
 * list with the playing song marked where it sits.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicUpNext(
    viewModel: PlayerViewModel,
    removal: com.ivor.ivormusic.ui.components.QueueRemovalController,
    onLoadMore: () -> Unit
) {
    val context = LocalContext.current
    val queue by viewModel.currentQueue.collectAsState()
    val currentQueueItemId by viewModel.currentQueueItemId.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.mapTo(HashSet()) { it.id } }
    var showSaveDialog by remember { mutableStateOf(false) }

    if (queue.isEmpty()) {
        ClassicPanelMessage(stringResource(R.string.ps_queue_empty))
        return
    }

    val currentIndex = remember(queue, currentQueueItemId) { queue.indexOfFirst { it.id == currentQueueItemId } }
    val listState = rememberFocusedQueueListState(currentQueueIndex = currentIndex, leadingItemCount = 1)
    val rowKeys = remember(queue) { queue.map { it.id } }
    val reorder = rememberQueueReorderState(
        listState = listState,
        keys = rowKeys,
        onMove = { from, to -> viewModel.moveQueueItem(from, to) },
        onSettle = {}
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "up_next_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cl_playing_next),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(
                    onClick = { showSaveDialog = true },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = stringResource(R.string.queue_save_as_playlist)
                    )
                }
            }
        }
        itemsIndexed(queue, key = { index, _ -> rowKeys.getOrElse(index) { "queue_$index" } }) { index, item ->
            val key = rowKeys.getOrElse(index) { "queue_$index" }
            val isCurrent = item.id == currentQueueItemId
            val isDragging = reorder.draggingKey == key
            QueueRowContainer(
                isDragging = isDragging,
                dragOffset = reorder.offsetFor(key),
                removeEnabled = queue.size > 1,
                onRemove = {
                    viewModel.removeQueueItem(item.id)
                    removal.onRemoved(item.song.title)
                },
                modifier = if (isDragging) Modifier else Modifier.animateItem()
            ) {
                ClassicSongRow(
                    song = item.song,
                    highlighted = isCurrent,
                    first = index == 0,
                    last = index == queue.lastIndex,
                    onClick = { viewModel.skipToQueueItem(item.id) },
                    modifier = Modifier.queueDragLongPress(reorder, key),
                    status = {
                        when {
                            item.song.id in downloadingIds -> CircularWavyProgressIndicator(modifier = Modifier.size(16.dp))
                            item.song.id in downloadedIds -> Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.song_options_downloaded),
                                tint = LocalContentColor.current.copy(alpha = 0.72f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    trailing = {
                        QueueDragHandle(
                            state = reorder,
                            rowKey = key,
                            tint = LocalContentColor.current.copy(alpha = 0.72f)
                        )
                    }
                )
            }
        }
        item(key = "up_next_more") {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                FilledTonalButton(onClick = onLoadMore, enabled = !isLoadingMore, shapes = ButtonDefaults.shapes()) {
                    if (isLoadingMore) {
                        LoadingIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.loading))
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.load_more))
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        CreatePlaylistDialog(
            initialName = stringResource(R.string.queue_save_default_name, remember { queuePlaylistDateText() }),
            onDismiss = { showSaveDialog = false },
            onCreate = { name, description ->
                showSaveDialog = false
                viewModel.saveQueueAsPlaylist(name, description) { savedName, trackCount ->
                    removal.announce(
                        context.resources.getQuantityString(
                            R.plurals.queue_saved_to_playlist, trackCount, trackCount, savedName
                        )
                    )
                }
            }
        )
    }
}

/**
 * Related: songs like the one playing, from YouTube Music's own radio for it,
 * asked for when this tab is opened (see [PlayerViewModel.relatedSongsFor]).
 * A tap starts that song's own radio, as it does in YouTube Music; the plus
 * queues it without leaving what is playing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicRelated(
    viewModel: PlayerViewModel,
    song: Song?,
    onArtistClick: (String) -> Unit,
    onAnnounce: (String) -> Unit
) {
    val context = LocalContext.current
    if (song == null) return
    if (song.source != SongSource.YOUTUBE) {
        ClassicPanelMessage(stringResource(R.string.cl_related_device))
        return
    }
    var attempt by remember(song.id) { mutableStateOf(0) }
    val related by produceState<List<Song>?>(initialValue = null, song.id, attempt) {
        value = null
        value = viewModel.relatedSongsFor(song)
    }
    val list = related
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        list.isEmpty() -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.cl_related_none),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { attempt++ }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.action_retry))
            }
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val artist = song.artist.takeIf { !isUnknownArtist(it) }
            if (artist != null) {
                item(key = "related_artist") {
                    Surface(
                        onClick = { onArtistClick(artist) },
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialShapes.Cookie9Sided.toShape(),
                                color = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Person, contentDescription = null)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                stringResource(R.string.cl_related_more_from, artist),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            item(key = "related_header") {
                Text(
                    stringResource(R.string.cl_related_like_this),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            itemsIndexed(list, key = { _, item -> "related_${item.id}" }) { index, item ->
                ClassicSongRow(
                    song = item,
                    highlighted = false,
                    first = index == 0,
                    last = index == list.lastIndex,
                    onClick = { viewModel.playSongRadio(item) },
                    trailing = {
                        IconButton(
                            onClick = {
                                viewModel.addToQueue(listOf(item))
                                onAnnounce(context.getString(R.string.cl_added_to_queue, item.title))
                            },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = stringResource(R.string.song_options_add_to_queue)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ClassicPanelMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * One row of a panel list, in the app's segmented-list geometry: 20dp at the
 * group's outer corners, 4dp between rows, so a list reads as one container.
 * The playing row is the accent container with its cover marked.
 */
@Composable
private fun ClassicSongRow(
    song: Song,
    highlighted: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit
) {
    val outer = 20.dp
    val inner = 4.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(
            topStart = if (first) outer else inner,
            topEnd = if (first) outer else inner,
            bottomStart = if (last) outer else inner,
            bottomEnd = if (last) outer else inner
        ),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))) {
                SongArtwork(
                    song = song,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (highlighted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = stringResource(R.string.cd_playing),
                            tint = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    status()
                }
            }
            trailing()
        }
    }
}

/** A [Morph] between two unit-square Material shapes, stretched to the composable. */
private class UnitMorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    return String.format(java.util.Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
