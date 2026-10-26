package com.ivor.ivormusic.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles widget button presses in the background. No UI, no flash —
 * behaves like a normal interactive widget.
 *
 * Toggle and next mirror the Glance actions' rules (see WidgetActions.kt):
 * a tap on an empty timeline restores it first, buffering counts as
 * playing, and a skip waits for the crossfade-intercepted command to
 * settle before reading back — otherwise the published snapshot would
 * still name the previous track.
 */
class WidgetButtonReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(WidgetActionActivity.EXTRA_ACTION) ?: return
        val index = intent.getIntExtra(WidgetActionActivity.EXTRA_INDEX, -1)

        // Use goAsync to keep the receiver alive for the coroutine
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    WidgetActionActivity.ACTION_TOGGLE -> {
                        val snapshot = withController(context) { controller ->
                            if (controller.mediaItemCount == 0) {
                                if (!controller.ensureRestored(playWhenRestored = true)) {
                                    return@withController null
                                }
                                return@withController controller.toSnapshot()
                            }
                            // Decide from live state, not from the last render.
                            if (controller.playWhenReady ||
                                controller.playbackState == Player.STATE_BUFFERING
                            ) {
                                controller.pause()
                            } else {
                                controller.play()
                            }
                            controller.toSnapshot()
                        } ?: return@launch
                        PlayerWidgets.publish(context, snapshot)
                    }
                    WidgetActionActivity.ACTION_NEXT -> {
                        // Every press reshapes the next button, immediately
                        // — not when the settled track publish lands 500ms
                        // later. The later publish's full render reads the
                        // advanced index, so it cannot undo the morph.
                        VinylClassic.cycleNextShape(context)
                        val sent = withController(context) { controller ->
                            if (!controller.ensureRestored(playWhenRestored = false)) {
                                return@withController false
                            }
                            controller.seekToNext()
                            true
                        } ?: false
                        if (sent) {
                            delay(500L)
                            val settled = withController(context) { it.toSnapshot() }
                            if (settled != null) PlayerWidgets.publish(context, settled)
                        }
                    }
                    WidgetActionActivity.ACTION_PREVIOUS -> {
                        withController(context) { controller ->
                            controller.seekToPrevious()
                            PlayerWidgets.publish(context, controller.toSnapshot())
                        }
                    }
                    WidgetActionActivity.ACTION_SHUFFLE -> {
                        withController(context) { controller ->
                            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                            PlayerWidgets.publish(context, controller.toSnapshot())
                        }
                    }
                    WidgetActionActivity.ACTION_CYCLE_REPEAT -> {
                        withController(context) { controller ->
                            controller.repeatMode = when (controller.repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_OFF ->
                                    androidx.media3.common.Player.REPEAT_MODE_ALL
                                androidx.media3.common.Player.REPEAT_MODE_ALL ->
                                    androidx.media3.common.Player.REPEAT_MODE_ONE
                                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                            }
                            PlayerWidgets.publish(context, controller.toSnapshot())
                        }
                    }
                    WidgetActionActivity.ACTION_PLAY_INDEX -> {
                        if (index >= 0) {
                            withController(context) { controller ->
                                controller.seekTo(index, 0)
                                controller.play()
                                PlayerWidgets.publish(context, controller.toSnapshot())
                            }
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
