package com.altrii.filter.data

import android.content.Context
import androidx.core.content.edit

class FilterPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var blockSocialMedia: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_SOCIAL, true)
        set(value) = prefs.edit { putBoolean(KEY_BLOCK_SOCIAL, value) }

    var blockYoutube: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_YOUTUBE, true)
        set(value) = prefs.edit { putBoolean(KEY_BLOCK_YOUTUBE, value) }

    var blockGambling: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_GAMBLING, true)
        set(value) = prefs.edit { putBoolean(KEY_BLOCK_GAMBLING, value) }

    companion object {
        private const val PREFS_NAME = "altrii_filter_preferences"
        private const val KEY_BLOCK_SOCIAL = "block_social"
        private const val KEY_BLOCK_YOUTUBE = "block_youtube"
        private const val KEY_BLOCK_GAMBLING = "block_gambling"
    }
}
