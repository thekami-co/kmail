package com.kmail.thekami.ui.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What a swipe on a message does. NONE turns that direction off. */
enum class SwipeAction { ARCHIVE, DELETE, STAR, READ, NONE }

/**
 * Inbox choices that belong to the person using the app, kept on this device.
 * Read them from anywhere with InboxPrefs.get(context).
 */
class InboxPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("inbox_prefs", Context.MODE_PRIVATE)

    private val _showPhotos = MutableStateFlow(prefs.getBoolean(KEY_PHOTOS, DEFAULT_PHOTOS))
    private val _swipeRight = MutableStateFlow(read(KEY_RIGHT, SwipeAction.ARCHIVE))
    private val _swipeLeft = MutableStateFlow(read(KEY_LEFT, SwipeAction.DELETE))

    /** Show sender profile pictures (from Gravatar) when a sender has one. */
    val showPhotos: StateFlow<Boolean> = _showPhotos
    val swipeRight: StateFlow<SwipeAction> = _swipeRight
    val swipeLeft: StateFlow<SwipeAction> = _swipeLeft

    fun setShowPhotos(value: Boolean) {
        prefs.edit().putBoolean(KEY_PHOTOS, value).apply()
        _showPhotos.value = value
    }

    fun setSwipeRight(action: SwipeAction) {
        prefs.edit().putString(KEY_RIGHT, action.name).apply()
        _swipeRight.value = action
    }

    fun setSwipeLeft(action: SwipeAction) {
        prefs.edit().putString(KEY_LEFT, action.name).apply()
        _swipeLeft.value = action
    }

    private fun read(key: String, default: SwipeAction): SwipeAction =
        runCatching { SwipeAction.valueOf(prefs.getString(key, null).orEmpty()) }.getOrDefault(default)

    companion object {
        private const val KEY_PHOTOS = "show_photos"
        private const val KEY_RIGHT = "swipe_right"
        private const val KEY_LEFT = "swipe_left"

        /** Photos need a request to gravatar.com with a hash of the sender's address. Set false to start with them off. */
        const val DEFAULT_PHOTOS = true

        @Volatile
        private var instance: InboxPrefs? = null

        fun get(context: Context): InboxPrefs =
            instance ?: synchronized(this) {
                instance ?: InboxPrefs(context.applicationContext).also { instance = it }
            }
    }
}
