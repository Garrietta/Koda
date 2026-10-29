package com.ivor.ivormusic

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ivor.ivormusic.data.CrashReporter
import com.ivor.ivormusic.data.LocalVideoThumbnail
import com.ivor.ivormusic.data.LocalVideoThumbnailFetcher
import com.ivor.ivormusic.data.PodcastRepository
import com.ivor.ivormusic.work.PodcastRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IvorMusicApplication : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Started-activity count, main thread only (lifecycle callbacks run
    // there): the app is foregrounded exactly when it goes 0 -> 1. Config
    // changes keep the count above zero (the new activity starts before
    // the old one stops), so they never fake a foreground.
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else can crash, so the bug reporter has a
        // file to offer on the next launch. It wraps - never replaces - the
        // platform handler.
        CrashReporter.install(this)
        // Reconcile the persisted periodic job with the user's current opt-in.
        // The setting handler does the same immediately when the value changes.
        com.ivor.ivormusic.work.UploadCheckWorker.sync(this)
        com.ivor.ivormusic.work.ScheduledBackupWorker.sync(this)
        // Podcast feeds refresh silently in the background whether or not
        // the app is opened; this only schedules the job, it never fetches.
        PodcastRefreshWorker.sync(this)
        // App-foreground podcast refresh: the first activity starting means
        // the app came to the foreground. Debounced in the repository to one
        // round per hour, shared with the tab-visible and worker triggers.
        registerActivityLifecycleCallbacks(podcastForegroundCallbacks)
        // Discord Rich Presence (no-ops until the user turns it on in Settings
        // AND the Discord app is installed and signed in).
        // Observes playback state only; it can never affect audio delivery,
        // and every failure stays contained so a missing Discord install
        // cannot reach startup.
        com.ivor.ivormusic.presence.DiscordPresenceManager.start(this)
    }

    /**
     * Fires the debounced all-feeds refresh on app foreground (see
     * [startedActivities]): the New episodes rail used to update only when
     * a podcast's detail page was opened.
     */
    private val podcastForegroundCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            if (++startedActivities == 1) {
                appScope.launch {
                    PodcastRepository(this@IvorMusicApplication).refreshAllFeeds()
                }
            }
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities--
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /**
     * The one Coil loader for the whole process. AsyncImage and every direct
     * ImageRequest (artwork color extraction, notification artwork, downloads)
     * resolve to this instance through Context.imageLoader, so they share one
     * memory cache, one disk cache and one connection pool instead of each
     * call site building its own loader.
     *
     * The only component added is the device-video frame fetcher, which is
     * keyed on its own [LocalVideoThumbnail] model and so cannot affect any
     * other image: every existing request still resolves exactly as it did.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(LocalVideoThumbnailFetcher.Factory(this@IvorMusicApplication))
                add(LocalVideoThumbnailFetcher.ThumbnailKeyer())
            }
            .build()
}
