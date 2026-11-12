package com.ivor.ivormusic.data

import android.content.Context

/**
 * Which updates Software Update follows: releases, or test builds (every
 * signed build CI makes, published as one rolling pre-release). A device
 * preference, not part of a backup: it describes how this install is kept
 * current, like the update prompt's own memory.
 */
object UpdateChannel {
    private const val PREFS = "update_channel"
    private const val KEY_TEST_BUILDS = "test_builds"

    /** Test builds unless chosen otherwise: they are what this fork is installed from. */
    fun testBuilds(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TEST_BUILDS, true)

    fun setTestBuilds(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TEST_BUILDS, on).apply()
    }
}
