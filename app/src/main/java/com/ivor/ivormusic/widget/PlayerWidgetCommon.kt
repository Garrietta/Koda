package com.ivor.ivormusic.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import com.ivor.ivormusic.service.MusicService
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * The widget family's data layer: what a widget knows, where it is kept, and
 * the one way to talk to the session.
 *
 * **Widgets never read the session in order to draw.** [MusicService] writes
 * the state from its own player callback - the one place that cannot be wrong
 * about it - and [PlayerWidgetHost] hands it to the compositions. A render is a
 * preference read. The version that bound a controller per redraw was wrong
 * constantly for three compounding reasons: the read happened at an arbitrary
 * moment relative to the change that triggered it, six widgets raced each other
 * for the same answer, and a bind that timed out blanked the home screen.
 *
 * A controller is bound for exactly two things: issuing a command a tap asked
 * for, and seeding the store the very first time. Both go through
 * [withController], which hops to the main thread - a [MediaController] may
 * only ever be touched on its application thread, and Glance runs every
 * `provideGlance` and `ActionCallback` on a background dispatcher, so direct
 * calls threw IllegalStateException into a catch block and silently did
 * nothing. That was "the buttons only work sometimes".
 */

/** What one render knows about playback. */
data class PlayerWidgetSnapshot(
    val hasMedia: Boolean,
    val isPlaying: Boolean,
    val title: String?,
    val artist: String?,
    val artworkUri: Uri?,
    val positionMs: Long,
    val durationMs: Long,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    /** The next few queue entries, in play order, for the Lineup widget. */
    val upNext: List<UpNextEntry>,
    /** The playing row's media id (a YouTube video id for streams). */
    val mediaId: String?,
    /** The playing row's album, when the metadata carries one. */
    val album: String?,
    /** Vibrant color extracted from artwork (ARGB int), null if unavailable. */
    val artworkColor: Int?,
    /**
     * Second album-derived color for the vinyl widget's center-label sticker
     * (ARGB int), null if unavailable. Always a DIFFERENT Palette swatch from
     * [artworkColor] (or a lightness-shifted derivation of it) so the sticker
     * and the buttons never share one tint.
     */
    val artworkLabelColor: Int?,
) {
    /** 0f..1f for the progress strip; 0 when the duration is unknown. */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        fun empty() = PlayerWidgetSnapshot(
            hasMedia = false,
            isPlaying = false,
            title = null,
            artist = null,
            artworkUri = null,
            positionMs = 0L,
            durationMs = 0L,
            hasNext = false,
            hasPrevious = false,
            shuffleEnabled = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            upNext = emptyList(),
            mediaId = null,
            album = null,
            artworkColor = null,
            artworkLabelColor = null,
        )
    }
}

/** One upcoming queue row. [index] addresses the timeline, never a media id. */
data class UpNextEntry(
    val index: Int,
    val title: String,
    val artist: String?,
)

/** How many queue rows the Lineup widget can ever want. */
internal const val UP_NEXT_LOOKAHEAD = 5

/**
 * The one place widget state lives, as a single JSON blob in its own
 * preference file. JSON rather than a key per field because the queue is a
 * list, and one string keeps a write atomic - a widget can never render half of
 * one update and half of the next.
 *
 * Deliberately not in `ivor_music_theme_prefs`: this is a cache of playback,
 * not a setting, and it has no business in backups.
 */
object PlayerWidgetStore {

    /**
     * JSON rather than a key per field because the queue is a list, and one
     * string keeps a write atomic - a widget can never render half of one
     * update and half of the next.
     *
     * Deliberately not in `ivor_music_theme_prefs`: this is a cache of
     * playback, not a setting, and it has no business in backups.
     */
    private const val PREFS = "koda_widget_state"
    private const val KEY_STATE = "state"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun write(context: Context, snapshot: PlayerWidgetSnapshot) {
        prefs(context).edit().putString(KEY_STATE, snapshot.toJson().toString()).apply()
    }

    /**
     * Synchronous variant for the one write that must not be lost to a dying
     * process: the seed-liveness correction (see [WidgetRevivePolicy]). An
     * async apply() queued behind a process kill may never flush, and the
     * next fresh process would re-read the stale playing flag.
     */
    fun writeSync(context: Context, snapshot: PlayerWidgetSnapshot) {
        prefs(context).edit().putString(KEY_STATE, snapshot.toJson().toString()).commit()
    }

    /** Null means nothing has ever been written - not "nothing is playing". */
    fun read(context: Context): PlayerWidgetSnapshot? {
        val raw = prefs(context).getString(KEY_STATE, null) ?: return null
        return snapshotFromJson(raw)
    }

    /**
     * The service is going away. Keeps the track so the widget still shows what
     * was on, but drops the playing flag - a widget claiming to play music that
     * stopped when the process died is the one lie this store can tell.
     */
    fun markStopped(context: Context) {
        val current = read(context) ?: return
        if (!current.isPlaying) return
        write(context, current.copy(isPlaying = false))
    }
}

/**
 * Pure JSON form of a widget snapshot, extracted from [PlayerWidgetStore] so
 * the store round-trip is unit-testable on the JVM: the store is the widget's
 * survival mechanism across process death, and a key lost or renamed here
 * silently reverts a widget to an empty frame on the next cold start.
 */
internal fun PlayerWidgetSnapshot.toJson(): JSONObject {
    return JSONObject().apply {
        put("hasMedia", hasMedia)
        put("isPlaying", isPlaying)
        put("title", title ?: JSONObject.NULL)
        put("artist", artist ?: JSONObject.NULL)
        put("artwork", artworkUri?.toString() ?: JSONObject.NULL)
        put("position", positionMs)
        put("duration", durationMs)
        put("hasNext", hasNext)
        put("hasPrevious", hasPrevious)
        put("shuffle", shuffleEnabled)
        put("repeat", repeatMode)
        put("mediaId", mediaId ?: JSONObject.NULL)
        put("album", album ?: JSONObject.NULL)
        put("artworkColor", artworkColor ?: JSONObject.NULL)
        put("artworkLabelColor", artworkLabelColor ?: JSONObject.NULL)
        put(
            "upNext",
            JSONArray().apply {
                upNext.forEach { entry ->
                    put(
                        JSONObject().apply {
                            put("index", entry.index)
                            put("title", entry.title)
                            put("artist", entry.artist ?: JSONObject.NULL)
                        }
                    )
                }
            }
        )
    }
}

/** Inverse of [PlayerWidgetSnapshot.toJson]; null when the blob is unreadable. */
internal fun snapshotFromJson(raw: String): PlayerWidgetSnapshot? {
    return try {
        val json = JSONObject(raw)
        val queue = json.optJSONArray("upNext")
        val entries = mutableListOf<UpNextEntry>()
        if (queue != null) {
            for (i in 0 until queue.length()) {
                val item = queue.optJSONObject(i) ?: continue
                val index = item.optInt("index", -1)
                if (index < 0) continue
                entries.add(
                    UpNextEntry(
                        index = index,
                        title = item.optString("title", ""),
                        artist = item.optString("artist", "").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
        PlayerWidgetSnapshot(
            hasMedia = json.optBoolean("hasMedia", false),
            isPlaying = json.optBoolean("isPlaying", false),
            title = json.optString("title", "").takeIf { it.isNotBlank() },
            artist = json.optString("artist", "").takeIf { it.isNotBlank() },
            artworkUri = json.optString("artwork", "").takeIf { it.isNotBlank() }
                ?.let(Uri::parse),
            positionMs = json.optLong("position", 0L),
            durationMs = json.optLong("duration", 0L),
            hasNext = json.optBoolean("hasNext", false),
            hasPrevious = json.optBoolean("hasPrevious", false),
            shuffleEnabled = json.optBoolean("shuffle", false),
            repeatMode = json.optInt("repeat", Player.REPEAT_MODE_OFF),
            upNext = entries,
            mediaId = json.optString("mediaId", "").takeIf { it.isNotBlank() },
            album = json.optString("album", "").takeIf { it.isNotBlank() },
            artworkColor = json.optInt("artworkColor", 0).takeIf { it != 0 },
            // Old blobs predate the field: optInt defaults to 0, which
            // reads back as null, so a stale store never breaks a render.
            artworkLabelColor = json.optInt("artworkLabelColor", 0).takeIf { it != 0 },
        )
    } catch (e: Exception) {
        KLog.w("PlayerWidget", "Widget state unreadable: ${e.message}")
        null
    }
}

/**
 * Pure decisions behind widget revival after the app process dies. The
 * Android-bound parts (controller binds, disk IO) are not reachable from a
 * JVM test; these are the decisions they depend on.
 *
 * Why revival needs decisions at all: every widget update path used to assume
 * the service was alive. A process killed in the background breaks all three
 * at once - the last published frame keeps claiming whatever it claimed, a
 * tap's controller bind races a cold service start, and one failed artwork
 * load latches a placeholder until a next publish that never comes. Each
 * decision below closes one of those.
 */
internal object WidgetRevivePolicy {

    /**
     * Widget taps are user-initiated: give a cold service start time to bind.
     * The old 3s default was written for a warm service; a dead process needs
     * service creation, engine init, and session restore, which routinely
     * exceeds it - and a timed-out tap silently did nothing, which read as
     * "controls froze".
     */
    const val TAP_BIND_TIMEOUT_MS = 10_000L

    /**
     * Fresh-process seed check: long enough for a live bind to answer, short
     * enough that a dead session does not stall the first frame behind the
     * static shell. Runs at most once per process (the seed latch).
     */
    const val SEED_LIVENESS_TIMEOUT_MS = 1_500L

    /**
     * Pick the snapshot a fresh process should render from a stored frame and
     * an optional live session read. A live session with media always wins -
     * it is fresher than any stored frame. Anything else means the stored
     * playing flag cannot be trusted: a session that cannot be reached is
     * gone, and a session that answers but holds no media ended without a
     * clean onDestroy, so a stored "playing" is corrected to paused rather
     * than rendered as a lie.
     */
    fun resolveSeedSnapshot(
        stored: PlayerWidgetSnapshot,
        live: PlayerWidgetSnapshot?,
    ): PlayerWidgetSnapshot =
        live?.takeIf { it.hasMedia } ?: stored.stoppedIfPlaying()

    private fun PlayerWidgetSnapshot.stoppedIfPlaying(): PlayerWidgetSnapshot =
        if (isPlaying) copy(isPlaying = false) else this

    /** Filename-safe key for the artwork disk cache: hex SHA-256 of the URI. */
    fun artworkCacheKey(artworkUri: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(artworkUri.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

/**
 * The last decoded cover, persisted in app-private files (not cacheDir, which
 * the system may evict exactly when the widget needs it most). One file, keyed
 * by artwork URI: a reseed after process death draws this immediately when
 * the network load fails, instead of latching a placeholder until a publish
 * that may never come. Written only from the live publish path; reads never
 * block a render - a missing or corrupt file is just a null.
 */
internal object WidgetArtworkDiskCache {

    private const val DIR = "widget_art"
    private const val EXT = ".jpg"

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    fun fileFor(context: Context, artworkUri: String): File =
        File(dir(context), WidgetRevivePolicy.artworkCacheKey(artworkUri) + EXT)

    fun write(context: Context, artworkUri: String, bitmap: Bitmap) {
        runCatching {
            val target = fileFor(context, artworkUri)
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            // One cover at a time: a new track must never leave the previous
            // track's art behind to be mistaken for it.
            dir(context).listFiles()?.forEach { file ->
                if (file != target) runCatching { file.delete() }
            }
        }
    }

    fun read(context: Context, artworkUri: String): Bitmap? = runCatching {
        val file = fileFor(context, artworkUri)
        if (!file.exists()) null else BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()
}

internal object PlayerWidgetReader {

    private const val TAG = "PlayerWidget"

    /**
     * One push decodes one cover for six widgets. Only the newest is kept - a
     * widget is always drawing the current track, so a second entry would only
     * ever be the one that just stopped being needed.
     */
    @Volatile
    private var artworkCache: Pair<String, Bitmap>? = null

    suspend fun loadArtwork(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val key = "$uri@$sizePx"
        artworkCache?.let { (cachedKey, bitmap) -> if (cachedKey == key) return bitmap }
        return try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .size(sizePx)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) artworkCache = key to bitmap
            bitmap
        } catch (e: kotlinx.coroutines.CancellationException) {
            // A newer push cancelled this one. Swallowed, it came back as "no
            // cover", which the host then published and kept: the next push
            // saw the same cover uri and reused the nothing it had stored.
            throw e
        } catch (e: Exception) {
            KLog.w(TAG, "Widget artwork failed: ${e.message}")
            null
        }
    }
}

/**
 * Read a player's current state. Valid on the player's own thread only, which
 * is the main thread for both callers - [MusicService]'s listener and a
 * controller inside [withController].
 */
internal fun Player.toWidgetSnapshot(): PlayerWidgetSnapshot {
    if (currentTimeline.isEmpty) return PlayerWidgetSnapshot.empty()
    val upcoming = mutableListOf<UpNextEntry>()
    var index = currentMediaItemIndex + 1
    while (index < mediaItemCount && upcoming.size < UP_NEXT_LOOKAHEAD) {
        val metadata = getMediaItemAt(index).mediaMetadata
        upcoming.add(
            UpNextEntry(
                index = index,
                title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown",
                artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() },
            )
        )
        index++
    }
    return PlayerWidgetSnapshot(
        hasMedia = true,
        // Intent, not achievement: pressing play swaps the glyph now instead of
        // after the first buffer, which is what every other transport surface
        // on the device does.
        isPlaying = playWhenReady,
        title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() },
        artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() },
        artworkUri = mediaMetadata.artworkUri?.takeIf { it.toString().isNotBlank() },
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = duration.takeIf { it > 0 } ?: 0L,
        hasNext = hasNextMediaItem(),
        hasPrevious = hasPreviousMediaItem(),
        shuffleEnabled = shuffleModeEnabled,
        repeatMode = repeatMode,
        upNext = upcoming,
        mediaId = currentMediaItem?.mediaId?.takeIf { it.isNotBlank() },
        album = mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() },
        artworkColor = null, // Enriched by PlayerWidgets.publish() via Palette
        artworkLabelColor = null, // Enriched alongside it, a different swatch
    )
}

internal fun MediaController.toSnapshot(): PlayerWidgetSnapshot = toWidgetSnapshot()

/**
 * Bind a throwaway [MediaController], run [block] on the main thread, and
 * release it again. Widgets live for days while controllers are expensive
 * session clients, and a held controller would keep the service bound forever.
 *
 * Returns null when the session could not be reached at all, which callers use
 * to tell "nothing is playing" apart from "could not ask".
 */
internal suspend fun <T> withController(
    context: Context,
    timeoutMs: Long = 3_000L,
    block: suspend (MediaController) -> T,
): T? = withContext(Dispatchers.Main) {
    val appContext = context.applicationContext
    val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
    val future = MediaController.Builder(appContext, token).buildAsync()
    var controller: MediaController? = null
    try {
        controller = withTimeoutOrNull(timeoutMs) { future.await() } ?: return@withContext null
        block(controller)
    } catch (e: Exception) {
        KLog.w("PlayerWidget", "Session call failed: ${e.message}")
        null
    } finally {
        // Always both: release() covers a connected controller, releaseFuture
        // covers one still connecting when the timeout fired. Skipping the
        // second leaks a binding that keeps the service alive for nothing.
        runCatching { controller?.release() }
        runCatching { MediaController.releaseFuture(future) }
    }
}
