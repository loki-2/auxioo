package org.oxycblt.auxio.library

import android.content.Context
import androidx.annotation.IdRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.BaseModel
import org.oxycblt.auxio.music.Header
import org.oxycblt.auxio.music.MusicStore
import org.oxycblt.auxio.recycler.DisplayMode
import org.oxycblt.auxio.recycler.SortMode
import org.oxycblt.auxio.settings.SettingsManager

/**
 * A [ViewModel] that manages what [LibraryFragment] is currently showing, and also the search
 * functionality.
 * @author OxygenCobalt
 */
class LibraryViewModel : ViewModel(), SettingsManager.Callback {
    private val mSortMode = MutableLiveData(SortMode.ALPHA_DOWN)
    private val mLibraryData = MutableLiveData(listOf<BaseModel>())
    private val mSearchResults = MutableLiveData(listOf<BaseModel>())
    private var mDisplayMode = DisplayMode.SHOW_ARTISTS
    private var mIsNavigating = false

    /** The current [SortMode] */
    val sortMode: LiveData<SortMode> get() = mSortMode
    /** The current library data */
    val libraryData: LiveData<List<BaseModel>> get() = mLibraryData
    /** The results from the last search query */
    val searchResults: LiveData<List<BaseModel>> get() = mSearchResults
    /** If LibraryFragment is already navigating */
    val isNavigating: Boolean get() = mIsNavigating

    private val settingsManager = SettingsManager.getInstance()
    private val musicStore = MusicStore.getInstance()

    init {
        settingsManager.addCallback(this)

        // Set up the display/sort modes
        mDisplayMode = settingsManager.libraryDisplayMode
        mSortMode.value = settingsManager.librarySortMode

        updateLibraryData()
    }

    // --- SEARCH FUNCTIONS ---

    /**
     * Perform a search of the music library, given a query.
     * Results are pushed to [searchResults].
     * @param query The query for this search
     * @param context The context needed to create the header text
     */
    fun doSearch(query: String, context: Context) {
        // Don't bother if the query is blank.
        if (query == "") {
            resetQuery()

            return
        }

        // Search MusicStore for all the items [Artists, Albums, Songs] that contain
        // the query, and update the LiveData with those items. This is done on a separate
        // thread as it can be a very long operation for large music libraries.
        viewModelScope.launch {
            val combined = mutableListOf<BaseModel>()
            val children = mDisplayMode.getChildren()

            // If the Library DisplayMode supports it, include artists / genres in the search.
            if (children.contains(DisplayMode.SHOW_GENRES)) {
                val genres = musicStore.genres.filter { it.name.contains(query, true) }

                if (genres.isNotEmpty()) {
                    combined.add(Header(name = context.getString(R.string.label_genres)))
                    combined.addAll(genres)
                }
            }

            if (children.contains(DisplayMode.SHOW_ARTISTS)) {
                val artists = musicStore.artists.filter { it.name.contains(query, true) }

                if (artists.isNotEmpty()) {
                    combined.add(Header(name = context.getString(R.string.label_artists)))
                    combined.addAll(artists)
                }
            }

            // Albums & Songs are always included.
            val albums = musicStore.albums.filter { it.name.contains(query, true) }

            if (albums.isNotEmpty()) {
                combined.add(Header(name = context.getString(R.string.label_albums)))
                combined.addAll(albums)
            }

            val songs = musicStore.songs.filter { it.name.contains(query, true) }

            if (songs.isNotEmpty()) {
                combined.add(Header(name = context.getString(R.string.label_songs)))
                combined.addAll(songs)
            }

            mSearchResults.value = combined
        }
    }

    /**
     * Reset the search query.
     */
    fun resetQuery() {
        mSearchResults.value = listOf()
    }

    // --- LIBRARY FUNCTIONS ---

    /**
     * Update the current [SortMode].
     * @param itemId The id of the menu item selected.
     */
    fun updateSortMode(@IdRes itemId: Int) {
        val mode = when (itemId) {
            R.id.option_sort_none -> SortMode.NONE
            R.id.option_sort_alpha_down -> SortMode.ALPHA_DOWN
            R.id.option_sort_alpha_up -> SortMode.ALPHA_UP

            else -> SortMode.NONE
        }

        if (mode != mSortMode.value) {
            mSortMode.value = mode
            settingsManager.librarySortMode = mode

            updateLibraryData()
        }
    }

    /**
     * Update the current navigation status
     * @param value Whether LibraryFragment is navigating or not
     */
    fun updateNavigationStatus(value: Boolean) {
        mIsNavigating = value
    }

    // --- OVERRIDES ---

    override fun onCleared() {
        super.onCleared()

        settingsManager.removeCallback(this)
    }

    override fun onLibDisplayModeUpdate(displayMode: DisplayMode) {
        mDisplayMode = displayMode

        updateLibraryData()
    }

    // --- UTILS ---

    /**
     * Shortcut function for updating the library data with the current [SortMode]/[DisplayMode]
     */
    private fun updateLibraryData() {
        mLibraryData.value = mSortMode.value!!.getSortedBaseModelList(
            musicStore.getListForDisplayMode(mDisplayMode)
        )
    }
}
