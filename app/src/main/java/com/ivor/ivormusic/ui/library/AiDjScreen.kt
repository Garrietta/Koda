package com.ivor.ivormusic.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.AiDjMix
import com.ivor.ivormusic.data.AiDjRepository
import com.ivor.ivormusic.data.DjVoiceRepository
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.launch
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
    /**
     * Start infinite extension for the mix just handed to [onPlayDjMix]:
     * title, seed songs, pools, and the voice toggle. Defaults to a no-op
     * so existing hosts stay finite; the DJ tab host wires it to the
     * player. Called after onPlayDjMix in every branch, so the session
     * always follows a real play.
     */
    onDjSessionStarted: (String, List<Song>, List<Song>, List<Song>, Boolean) -> Unit =
        { _, _, _, _, _ -> },
    onShuffleQueue: (List<Song>) -> Unit = {},
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
    // A tab has nowhere to go back to: the pill-bar tab hides the back arrow.
    showBack: Boolean = true,
    // Hoisted by the caller when the screen is a tab, so re-tapping the tab
    // scrolls to the top and the position survives a tab switch.
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
) {
    val context = LocalContext.current
    val repository = remember { AiDjRepository(context.applicationContext) }
    val voiceRepository = remember { DjVoiceRepository(context.applicationContext) }
    val hasKey by repository.hasApiKey.collectAsState()
    val djVoiceEnabled by repository.djVoiceEnabled.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val playHistory by viewModel.playHistory.collectAsState()

    // Recent plays: last 30, newest first, podcasts excluded — the familiar
    // pool for the instant mix, same construction as MusicService's auto-DJ.
    // The page used to generate from liked + downloaded songs only, which
    // has no discovery mechanism at all — every page mix came out 100%
    // liked. The instant mix is 60% familiar (recent + liked) and 40%
    // YouTube-search-resolved discoveries, placed by flow rather than a
    // fixed cadence (see generateInstantMix).
    val recent = remember(playHistory) {
        val seen = HashSet<String>()
        val out = mutableListOf<Song>()
        for (entry in playHistory) {
            if (entry.source == SongSource.PODCAST) continue
            if (!seen.add(entry.songId)) continue
            out += Song.fromYouTube(
                videoId = entry.songId,
                title = entry.title,
                artist = entry.artist,
                album = entry.album,
                duration = entry.duration,
                thumbnailUrl = entry.thumbnailUrl,
            )
            if (out.size >= 30) break
        }
        out
    }

    // The DJ pool shown in the hero ("N songs in the crates").
    val pool = remember(recent, likedSongs) {
        (recent + likedSongs).distinctBy { it.id }
    }

    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Voice warmup gating: a freshly generated mix is not complete until
    // its DJ intros are pre-run and cached. While warming up, the page
    // shows a warmup progress state instead of the mix.
    var warmingUp by remember { mutableStateOf(false) }
    var warmupProgress by remember { mutableFloatStateOf(0f) }
    // Key of the mix whose voice is warmed (title + size + track ids).
    // playMix skips inline synthesis only for exactly this mix.
    var warmedMixKey by remember { mutableStateOf<String?>(null) }
    fun djMixKey(m: AiDjMix) =
        "${m.title}|${m.tracks.size}|${m.tracks.map { it.song.id }.hashCode()}"
    // Open on the cached mix when one exists; only the Generate/Regenerate
    // buttons (or the Home emblem) build a fresh one.
    val cachedMix by repository.cachedMix.collectAsState()
    var mix by remember { mutableStateOf<AiDjMix?>(null) }
    LaunchedEffect(cachedMix) {
        // Never steal a mix that generate() is still warming up: the gate
        // is the whole point, and cacheMix lands before warmup finishes.
        // A local, not a smart cast: cachedMix is a delegated property,
        // which the compiler cannot smart-cast across the null check.
        if (mix == null && !generating && !warmingUp) {
            val cached = cachedMix
            mix = cached
            // Silent background warmup for the cached mix, so the first
            // play after opening the tab is instant. No UI state: the mix
            // is already shown; this only fills the voice cache.
            if (cached != null && djVoiceEnabled &&
                warmedMixKey != djMixKey(cached)
            ) {
                voiceRepository.warmupMix(cached) {}
                warmedMixKey = djMixKey(cached)
            }
        }
    }
    // Daily auto-refresh: if the cached mix is older than 24h, quietly build
    // a fresh one in the background. The cached mix stays visible meanwhile.
    val autoRefresh by repository.autoRefresh.collectAsState()
    LaunchedEffect(autoRefresh, recent, likedSongs) {
        if (autoRefresh && hasKey && repository.isCacheStale() && !generating && !warmingUp) {
            generating = true
            repository.generateInstantMix(recent, likedSongs).fold(
                onSuccess = {
                    repository.cacheMix(it)
                    // Warm the new mix's voice BEFORE it replaces the old
                    // one, so the first play of the refreshed mix is
                    // instant too. The stale mix stays visible meanwhile.
                    voiceRepository.warmupMix(it) {}
                    warmedMixKey = djMixKey(it)
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
        if (generating || warmingUp) return
        generating = true
        error = null
        mix = null
        scope.launch {
            repository.generateInstantMix(recent, likedSongs).fold(
                onSuccess = {
                    repository.cacheMix(it)
                    // Gate completion on the voice warmup: the mix must
                    // not read as complete until every intro is pre-run
                    // and cached, or the first press of play pays the
                    // synthesis delay instead. generating ends here so
                    // the page shows the warmup state, not the spinner.
                    generating = false
                    warmingUp = true
                    warmupProgress = 0f
                    voiceRepository.warmupMix(it) { progress ->
                        warmupProgress = progress
                    }
                    warmedMixKey = djMixKey(it)
                    warmingUp = false
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
            onDjSessionStarted(
                mix.title, mix.tracks.map { it.song }, recent, likedSongs, djVoiceEnabled
            )
            return
        }
        if (warmedMixKey == djMixKey(mix)) {
            // Already warmed: the queue builds from cache only, so there
            // is no voice delay to show progress for. Missing intros (a
            // line that failed both TTS engines) are simply skipped.
            scope.launch {
                onPlayDjMix(
                    voiceRepository.buildQueueWithIntros(mix, synthesizeMissing = false)
                )
                onDjSessionStarted(
                    mix.title, mix.tracks.map { it.song }, recent, likedSongs, djVoiceEnabled
                )
            }
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
                onDjSessionStarted(
                    mix.title, mix.tracks.map { it.song }, recent, likedSongs, djVoiceEnabled
                )
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
    val listState = listState ?: rememberLazyListState()
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
    // The AI DJ page's OWN ground, not the shared playlist ground: the
    // shared ground is primary-family, and so is the now-playing pill that
    // floats over this page — scrolled far down, the pill's edge vanished
    // into the page behind it. The DJ ground is the tertiary family
    // instead (see aiDjPageGround): still dynamic, still flat the whole
    // way down, but always a different hue family from the pill, so bar
    // and background stay distinguishable however the theme moves. The
    // hero dissolves into it and the track list stands on it, exactly as
    // the shared ground works everywhere else — only the family differs.
    val ground = aiDjPageGround()

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
                        if (showBack) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
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
            warmingUp -> {
                AiDjBusy(
                    message = stringResource(R.string.aidj_warming_voice),
                    progress = warmupProgress,
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
                val trackCount = currentMix?.tracks?.size ?: pool.size
                // Read here, in the composable body: the LazyColumn's DSL
                // scope below is not a @Composable context, so a
                // CompositionLocal read inside it does not compile.
                val nowPlaying = com.ivor.ivormusic.ui.components.LocalNowPlaying.current
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ground),
                    contentPadding = PaddingValues(
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    // Header: the playlist page's hero, with the DJ's abstract
                    // art as the cover — same banner, same parallax, same
                    // dissolve into the same ground — and the title below it
                    // on the page's rail, never centered on the art.
                    item(key = "dj_header") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PlaylistHeroBanner(
                                headerCollapse = headerCollapse,
                                // The hero's own drawn wash ends at this
                                // ground (its bottom stop), so the foot
                                // fade follows the art's last row down
                                // into the exact ground the list stands
                                // on — one consistent downward motion.
                                handoffGround = ground,
                            ) {
                                AiDjHeroArtwork(modifier = Modifier.fillMaxSize())
                            }
                            PlaylistPageTitle(
                                title = currentMix?.title ?: stringResource(R.string.aidj_title),
                                facts = stringResource(R.string.aidj_pool_size, trackCount),
                                ink = aiDjOnGround(),
                            )
                            // DJ extras, kept in their places: the voice
                            // toggle above the playback rows, New mix under
                            // Play all, the DJ lines inside the track rows.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PLAYLIST_GUTTER)
                            ) {
                                if (currentMix == null) {
                                    Text(
                                        stringResource(R.string.aidj_intro),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = aiDjOnGround().copy(alpha = 0.8f),
                                        modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 14.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.aidj_dj_voice_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = aiDjOnGround(),
                                    )
                                    Switch(
                                        checked = djVoiceEnabled,
                                        onCheckedChange = { repository.setDjVoiceEnabled(it) },
                                    )
                                }
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
                                        modifier = Modifier.padding(top = 14.dp),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                ) {
                                    FilledTonalButton(
                                        onClick = ::generate,
                                        enabled = pool.isNotEmpty(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
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
                                    Text(
                                        currentError,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    if (currentMix != null) {
                        val tracks = currentMix.tracks
                        item {
                            TrackSectionHeader(
                                countLabel = if (tracks.size == 1) "1 track" else "${tracks.size} tracks",
                                manageEnabled = false,
                                canEdit = false,
                                onToggleEdit = {},
                                ink = aiDjOnGround(),
                            )
                        }
                        itemsIndexed(
                            tracks,
                            key = { _, track -> "dj_${track.song.id}" },
                        ) { index, track ->
                            val song = track.song
                            val isFirstTrack = index == 0
                            val isLastTrack = index == tracks.lastIndex
                            // The playlist page's connected group: rows sit
                            // flush inside one visual container — round outer
                            // corners, small seams — with the group's 20dp
                            // inset on the item, so the geometry is the same
                            // by the same tokens, not by matching numbers.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = if (isFirstTrack) 12.dp else 0.dp,
                                        bottom = if (isLastTrack) 16.dp else ListItemDefaults.SegmentedGap
                                    )
                            ) {
                                PlaylistTrackRow(
                                    song = song,
                                    isCurrent = nowPlaying.songId == song.id,
                                    isPlaying = nowPlaying.isPlaying,
                                    manageEnabled = false,
                                    reorderEnabled = false,
                                    isDragging = false,
                                    dragHandleModifier = Modifier,
                                    onClick = {
                                        onPlayQueue(
                                            tracks.map { it.song },
                                            song,
                                        )
                                    },
                                    onRemove = {},
                                    extraLine = track.djLine,
                                    topCorner = if (isFirstTrack) 16.dp else 4.dp,
                                    bottomCorner = if (isLastTrack) 16.dp else 4.dp,
                                    rowSurface = aiDjRaisedSurface(),
                                    rowInk = aiDjOnGround(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Abstract code-drawn header artwork for the AI DJ page: the page's own
 * cover, in the playlist page's primary family, dissolving into the AI DJ
 * page's own tertiary ground (not the shared playlist ground) so the art
 * hands off seamlessly to the background the list stands on. Soft circles,
 * ring arcs and sparkle motifs, in the spirit of the playlist headers.
 * Drawn in code so it follows the theme; no bundled bitmaps. Positions are
 * fixed fractions, never random per recomposition.
 */
@Composable
private fun AiDjHeroArtwork(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ground = aiDjPageGround()
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Base wash, primary deep at the top-left dissolving to the page's
        // ground at the bottom, so the art hands off seamlessly.
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(scheme.primary, ground),
                start = Offset(0f, 0f),
                end = Offset(w * 0.35f, h),
            )
        )
        // Soft circles.
        drawCircle(
            color = scheme.onPrimaryContainer.copy(alpha = 0.16f),
            radius = w * 0.44f,
            center = Offset(w * 0.85f, h * 0.12f),
        )
        drawCircle(
            color = scheme.primary.copy(alpha = 0.35f),
            radius = w * 0.30f,
            center = Offset(w * 0.10f, h * 0.82f),
        )
        drawCircle(
            color = scheme.onPrimaryContainer.copy(alpha = 0.10f),
            radius = w * 0.17f,
            center = Offset(w * 0.55f, h * 0.66f),
        )
        drawCircle(
            color = scheme.onPrimaryContainer.copy(alpha = 0.12f),
            radius = w * 0.09f,
            center = Offset(w * 0.30f, h * 0.30f),
        )
        // A ring arc for motion.
        drawArc(
            color = scheme.onPrimaryContainer.copy(alpha = 0.20f),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.52f, -h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, w * 0.72f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.035f),
        )
        // Sparkles: 4-point stars.
        drawPath(sparklePath(w * 0.22f, h * 0.22f, w * 0.055f), scheme.onPrimaryContainer.copy(alpha = 0.50f))
        drawPath(sparklePath(w * 0.70f, h * 0.44f, w * 0.038f), scheme.onPrimaryContainer.copy(alpha = 0.35f))
        drawPath(sparklePath(w * 0.42f, h * 0.80f, w * 0.046f), scheme.onPrimaryContainer.copy(alpha = 0.40f))
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
