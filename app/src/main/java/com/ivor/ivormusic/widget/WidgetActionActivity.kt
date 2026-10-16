package com.ivor.ivormusic.widget

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Transparent activity that handles widget button presses. Glance's
 * actionRunCallback stopped firing, so widget buttons now launch this
 * activity which performs the transport action and finishes immediately.
 */
class WidgetActionActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra(EXTRA_ACTION)
        scope.launch {
            when (action) {
                ACTION_TOGGLE -> {
                    withController(this@WidgetActionActivity) { controller ->
                        if (controller.playWhenReady) controller.pause()
                        else controller.play()
                        PlayerWidgets.publish(
                            this@WidgetActionActivity,
                            controller.toSnapshot()
                        )
                    }
                }
                ACTION_NEXT -> {
                    withController(this@WidgetActionActivity) { controller ->
                        controller.seekToNext()
                        PlayerWidgets.publish(
                            this@WidgetActionActivity,
                            controller.toSnapshot()
                        )
                    }
                }
                ACTION_PREVIOUS -> {
                    withController(this@WidgetActionActivity) { controller ->
                        controller.seekToPrevious()
                        PlayerWidgets.publish(
                            this@WidgetActionActivity,
                            controller.toSnapshot()
                        )
                    }
                }
                ACTION_SHUFFLE -> {
                    withController(this@WidgetActionActivity) { controller ->
                        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                        PlayerWidgets.publish(
                            this@WidgetActionActivity,
                            controller.toSnapshot()
                        )
                    }
                }
                ACTION_PLAY_INDEX -> {
                    val index = intent.getIntExtra(EXTRA_INDEX, -1)
                    if (index >= 0) {
                        withController(this@WidgetActionActivity) { controller ->
                            controller.seekTo(index, 0)
                            controller.play()
                            PlayerWidgets.publish(
                                this@WidgetActionActivity,
                                controller.toSnapshot()
                            )
                        }
                    }
                }
                ACTION_CYCLE_REPEAT -> {
                    withController(this@WidgetActionActivity) { controller ->
                        controller.repeatMode = when (controller.repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_OFF ->
                                androidx.media3.common.Player.REPEAT_MODE_ALL
                            androidx.media3.common.Player.REPEAT_MODE_ALL ->
                                androidx.media3.common.Player.REPEAT_MODE_ONE
                            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                        }
                        PlayerWidgets.publish(
                            this@WidgetActionActivity,
                            controller.toSnapshot()
                        )
                    }
                }
            }
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACTION = "widget_action"
        const val EXTRA_INDEX = "widget_index"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREVIOUS = "previous"
        const val ACTION_SHUFFLE = "shuffle"
        const val ACTION_PLAY_INDEX = "play_index"
        const val ACTION_CYCLE_REPEAT = "cycle_repeat"
    }
}
