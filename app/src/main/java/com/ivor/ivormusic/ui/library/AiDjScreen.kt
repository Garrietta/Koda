package com.ivor.ivormusic.ui.library

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.AiDjMix
import com.ivor.ivormusic.data.AiDjRepository
import com.ivor.ivormusic.data.DjVoiceRepository
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.theme.MontserratFamily
import com.ivor.ivormusic.ui.theme.windowDpSize
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The AI DJ: generates a mix from the user's library via the Google AI API,
 * guided by the custom prompt in Settings. The result can be played right
 * away or saved as a local playlist.
 *
 * With DJ voice enabled, the DJ's intro lines are synthesized with the
 * device's text-to-speech and interleaved with the songs in the queue,
 * Spotify AI DJ style. Intros are skippable queue items and never touch
 * listening history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDjScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onPlayDjMix: (List<Song>) -> Unit,
    onShuffleQueue: (List<Song>) -> Unit = {},
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val repository = remember { AiDjRepository(context.applicationContext) }
    val voiceRepository = remember { DjVoiceRepository(context.applicationContext) }
    val hasKey by repository.hasApiKey.collectAsState()
    val djVoiceEnabled by repository.djVoiceEnabled.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()

    // The DJ pool: what the user owns and loves, deduplicated.
    val library = remember(likedSongs, downloadedSongs) {
        (likedSongs + downloadedSongs).distinctBy { it.id }
    }

    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Open on the cached mix when one exists; only the Generate/Regenerate
    // buttons (or the Home emblem) build a fresh one.
    val cachedMix by repository.cachedMix.collectAsState()
    var mix by remember { mutableStateOf<AiDjMix?>(null) }
    LaunchedEffect(cachedMix) {
        if (mix == null) mix = cachedMix
    }
    // Daily auto-refresh: if the cached mix is older than 24h, quietly build
    // a fresh one in the background. The cached mix stays visible meanwhile.
    val autoRefresh by repository.autoRefresh.collectAsState()
    LaunchedEffect(autoRefresh, library) {
        if (autoRefresh && hasKey && repository.isCacheStale() && !generating) {
            generating = true
            repository.generateMix(library).fold(
                onSuccess = {
                    repository.cacheMix(it)
                    mix = it
                },
                onFailure = { /* keep the stale cache; user can retry */ },
            )
            generating = false
        }
    }
    var saving by remember { mutableStateOf(false) }
    var preparingVoice by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun generate() {
        if (generating) return
        generating = true
        error = null
        mix = null
        scope.launch {
            repository.generateMix(library).fold(
                onSuccess = {
                    repository.cacheMix(it)
                    mix = it
                },
                onFailure = { error = it.message ?: "Generation failed" },
            )
            generating = false
        }
    }

    /**
     * Build the playback queue for [mix]: with DJ voice on, tracks carrying
     * a transition line are preceded by its synthesized spoken intro.
     */
    fun playMix(mix: AiDjMix) {
        if (preparingVoice) return
        if (!djVoiceEnabled) {
            onPlayDjMix(mix.tracks.map { it.song })
            return
        }
        preparingVoice = true
        voiceProgress = 0f
        error = null
        scope.launch {
            try {
                val queue = voiceRepository.buildQueueWithIntros(mix) { progress ->
                    voiceProgress = progress
                }
                onPlayDjMix(queue)
            } finally {
                preparingVoice = false
            }
        }
    }

    /**
     * Save the mix as a local playlist. Hoisted so the TopAppBar overflow
     * menu can reach it as well as the body.
     */
    fun saveAsPlaylist(mix: AiDjMix) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val date = SimpleDateFormat(
                    "MMM d", Locale.getDefault()
                ).format(Date())
                viewModel.createLocalPlaylistWithSongs(
                    name = "${mix.title} • $date",
                    description = mix.tracks.mapNotNull { it.djLine }
                        .take(3).joinToString(" / "),
                    songs = mix.tracks.map { it.song },
                )
            } finally {
                saving = false
            }
        }
    }

    // Collapsible hero header, the playlist page's pattern: how far the hero
    // has scrolled away drives the app bar fade and the artwork parallax.
    // Kept as State objects read only inside graphicsLayer/drawBehind lambdas,
    // so a scroll never recomposes the page.
    val listState = rememberLazyListState()
    val headerCollapse = remember {
        derivedStateOf {
            val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            when {
                header == null -> 1f
                header.size <= 0 -> 0f
                else -> (-header.offset.toFloat() / header.size).coerceIn(0f, 1f)
            }
        }
    }
    val barAlpha = remember {
        derivedStateOf { (headerCollapse.value / 0.55f).coerceIn(0f, 1f) }
    }
    val ground = aidjPageGround()
    val groundInk = MaterialTheme.colorScheme.onTertiaryContainer

    Scaffold(
        topBar = {
            // The bar settles into the page's ground: transparent over the
            // hero, fading to the ground as the header scrolls away.
            Box(modifier = Modifier.drawBehind { drawRect(ground, alpha = barAlpha.value) }) {
                TopAppBar(
                    title = {
                        Text(
                            mix?.title ?: stringResource(R.string.aidj_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer {
                                alpha = barAlpha.value
                                translationY = (1f - barAlpha.value) * 20.dp.toPx()
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        if (mix != null) {
                            var menuOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.aidj_save_playlist)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        mix?.let { saveAsPlaylist(it) }
                                    },
                                    enabled = !saving,
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.aidj_open_settings),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            }
        }
    ) { padding ->
        when {
            !hasKey -> {
                AiDjNeedsKey(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                )
            }
            generating -> {
                AiDjBusy(
                    message = stringResource(R.string.aidj_generating),
                    progress = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            preparingVoice -> {
                AiDjBusy(
                    message = stringResource(R.string.aidj_preparing_voice),
                    progress = voiceProgress,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            else -> {
                val currentMix = mix
                val currentError = error
                val trackCount = currentMix?.tracks?.size ?: library.size
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Hero: abstract code-drawn artwork fills the top edge to
                    // edge under the status bar, then dissolves into the
                    // page's ground - the playlist page's immersive top, for
                    // the DJ. The title sits below the art on the ground, so
                    // it reads in both themes on every palette.
                    item(key = "dj_hero") {
                        val windowHeight = windowDpSize().height
                        Column(modifier = Modifier.fillMaxWidth()) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val heroHeight = (maxWidth * 0.72f)
                                    .coerceAtMost(windowHeight * 0.34f)
                                    .coerceAtLeast(200.dp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(heroHeight)
                                        .clipToBounds()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .graphicsLayer {
                                                // Scroll-driven, not time-driven:
                                                // the art trails the list.
                                                val collapse = headerCollapse.value
                                                translationY = collapse * size.height * 0.24f
                                                alpha = 1f - collapse * 0.55f
                                            }
                                    ) {
                                        AiDjHeroArtwork(modifier = Modifier.fillMaxSize())
                                    }
                                    // Where the art hands the page over: a
                                    // plain fade in the page's ground color.
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    0f to ground.copy(alpha = 0f),
                                                    0.58f to ground.copy(alpha = 0f),
                                                    0.86f to ground.copy(alpha = 0.85f),
                                                    1f to ground
                                                )
                                            )
                                    )
                                    // Top fade: back and the actions need
                                    // ground to sit on whatever the art is.
                                    // Faded to the same color at zero alpha
                                    // rather than to Transparent, which is
                                    // transparent black and greys the band in
                                    // a light theme.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(132.dp)
                                            .align(Alignment.TopCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        ground.copy(alpha = 0.55f),
                                                        ground.copy(alpha = 0f)
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                            // Title and subtitle, centered on the ground.
                            Text(
                                text = currentMix?.title ?: stringResource(R.string.aidj_title),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = MontserratFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.02).em,
                                    lineHeight = 1.08.em
                                ),
                                color = groundInk,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 14.dp),
                            )
                            Text(
                                text = stringResource(R.string.aidj_pool_size, trackCount),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = groundInk.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                    // Controls: DJ voice toggle, then the playlist page's
                    // playback rows - Play all / Shuffle, then New mix /
                    // Download.
                    item(key = "dj_controls") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 18.dp),
                        ) {
                            if (currentMix == null) {
                                Text(
                                    stringResource(R.string.aidj_intro),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = groundInk.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.aidj_dj_voice_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = groundInk,
                                )
                                Switch(
                                    checked = djVoiceEnabled,
                                    onCheckedChange = { repository.setDjVoiceEnabled(it) },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            // Playlist page layout: Row 1 is Play all /
                            // Shuffle, Row 2 is the new-mix button under Play
                            // all and Download under Shuffle.
                            if (currentMix != null) {
                                CollectionPlaybackActions(
                                    onPlay = { playMix(currentMix) },
                                    // Shuffle plays the mix's tracks as a plain
                                    // shuffled queue; the DJ mix itself (Play
                                    // all) always stays strictly ordered.
                                    onShuffle = {
                                        onShuffleQueue(currentMix.tracks.map { it.song })
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Button(
                                    onClick = ::generate,
                                    enabled = library.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        if (currentMix == null) Icons.Rounded.AutoAwesome
                                        else Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        if (currentMix == null) stringResource(R.string.aidj_generate)
                                        else stringResource(R.string.aidj_new_mix)
                                    )
                                }
                                if (currentMix != null) {
                                    com.ivor.ivormusic.ui.downloads.MusicPlaylistDownloadAction(
                                        playlistId = "aidj-mix",
                                        playlistTitle = currentMix.title,
                                        songs = currentMix.tracks.map { it.song },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            if (currentError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    currentError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    if (currentMix != null) {
                        itemsIndexed(
                            currentMix.tracks,
                            key = { _, track -> "dj_${track.song.id}" },
                        ) { index, track ->
                            AiDjTrackRow(
                                index = index + 1,
                                song = track.song,
                                djLine = track.djLine,
                                onClick = {
                                    onPlayQueue(
                                        currentMix.tracks.map { it.song },
                                        track.song,
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The AI DJ page's ground: the tertiary equivalent of the playlist page's
 * ground - left alone in a dark scheme, deepened toward tertiary in a light
 * one, so onTertiaryContainer stays readable on it by M3 construction.
 */
@Composable
private fun aidjPageGround(): Color {
    val scheme = MaterialTheme.colorScheme
    if (scheme.background.luminance() < 0.5f) return scheme.tertiaryContainer
    return lerp(scheme.tertiaryContainer, scheme.tertiary, 0.3f)
}

/**
 * Abstract code-drawn header artwork for the AI DJ page: a tertiary wash
 * with soft circles, ring arcs and sparkle motifs, in the spirit of the
 * playlist headers. Drawn in code so it follows the theme; no bundled
 * bitmaps. Positions are fixed fractions, never random per recomposition.
 */
@Composable
private fun AiDjHeroArtwork(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Base wash, tertiary deep at the top-left dissolving to container.
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(scheme.tertiary, scheme.tertiaryContainer),
                start = Offset(0f, 0f),
                end = Offset(w * 0.35f, h),
            )
        )
        // Soft circles.
        drawCircle(
            color = scheme.onTertiary.copy(alpha = 0.16f),
            radius = w * 0.44f,
            center = Offset(w * 0.85f, h * 0.12f),
        )
        drawCircle(
            color = scheme.tertiary.copy(alpha = 0.35f),
            radius = w * 0.30f,
            center = Offset(w * 0.10f, h * 0.82f),
        )
        drawCircle(
            color = scheme.onTertiary.copy(alpha = 0.10f),
            radius = w * 0.17f,
            center = Offset(w * 0.55f, h * 0.66f),
        )
        drawCircle(
            color = scheme.onTertiaryContainer.copy(alpha = 0.12f),
            radius = w * 0.09f,
            center = Offset(w * 0.30f, h * 0.30f),
        )
        // A ring arc for motion.
        drawArc(
            color = scheme.onTertiary.copy(alpha = 0.20f),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.52f, -h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, w * 0.72f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.035f),
        )
        // Sparkles: 4-point stars.
        drawPath(sparklePath(w * 0.22f, h * 0.22f, w * 0.055f), scheme.onTertiary.copy(alpha = 0.50f))
        drawPath(sparklePath(w * 0.70f, h * 0.44f, w * 0.038f), scheme.onTertiary.copy(alpha = 0.35f))
        drawPath(sparklePath(w * 0.42f, h * 0.80f, w * 0.046f), scheme.onTertiary.copy(alpha = 0.40f))
    }
}

/** A 4-point sparkle star centered at (cx, cy) with radius r. */
private fun sparklePath(cx: Float, cy: Float, r: Float): Path {
    val k = 0.14f
    return Path().apply {
        moveTo(cx, cy - r)
        cubicTo(cx + r * k, cy - r * k, cx + r * k, cy - r * k, cx + r, cy)
        cubicTo(cx + r * k, cy + r * k, cx + r * k, cy + r * k, cx, cy + r)
        cubicTo(cx - r * k, cy + r * k, cx - r * k, cy + r * k, cx - r, cy)
        cubicTo(cx - r * k, cy - r * k, cx - r * k, cy - r * k, cx, cy - r)
        close()
    }
}

@Composable
private fun AiDjBusy(
    message: String,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            if (progress == null) {
                CircularProgressIndicator()
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiDjNeedsKey(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.aidj_needs_key_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.aidj_needs_key_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.aidj_open_settings))
        }
    }
}

@Composable
private fun AiDjTrackRow(
    index: Int,
    song: Song,
    djLine: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        val art = song.albumArtUri ?: song.highResThumbnailUrl ?: song.thumbnailUrl
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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
            if (!djLine.isNullOrBlank()) {
                Text(
                    djLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.aidj_play),
            )
        }
    }
}
