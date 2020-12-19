package org.oxycblt.auxio.music

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.logD
import org.oxycblt.auxio.music.processing.MusicLoader
import org.oxycblt.auxio.music.processing.MusicLoaderResponse
import org.oxycblt.auxio.music.processing.MusicSorter
import org.oxycblt.auxio.recycler.DisplayMode

/**
 * The main storage for music items. Use [MusicStore.getInstance] to get the single instance of it.
 * TODO: Completely rewrite this system.
 * @author OxygenCobalt
 */
class MusicStore private constructor() {
    private var mGenres = listOf<Genre>()
    val genres: List<Genre> get() = mGenres

    private var mArtists = listOf<Artist>()
    val artists: List<Artist> get() = mArtists

    private var mAlbums = listOf<Album>()
    val albums: List<Album> get() = mAlbums

    private var mSongs = listOf<Song>()
    val songs: List<Song> get() = mSongs

    /** All parent models loaded by Auxio */
    val parents: MutableList<BaseModel> by lazy {
        val parents = mutableListOf<BaseModel>()
        parents.addAll(mGenres)
        parents.addAll(mArtists)
        parents.addAll(mAlbums)
        parents
    }

    var loaded = false
        private set

    /**
     * Load/Sort the entire music library.
     * ***THIS SHOULD ONLY BE RAN FROM AN IO THREAD.***
     * @param app [Application] required to load the music.
     */
    suspend fun load(app: Application): MusicLoaderResponse {
        return withContext(Dispatchers.IO) {
            this@MusicStore.logD("Starting initial music load...")

            val start = System.currentTimeMillis()

            // Get the placeholder strings, which are used by MusicLoader & MusicSorter for
            // any music that doesn't have metadata.
            val genrePlaceholder = app.getString(R.string.placeholder_genre)
            val artistPlaceholder = app.getString(R.string.placeholder_artist)
            val albumPlaceholder = app.getString(R.string.placeholder_album)

            val loader = MusicLoader(
                app.contentResolver,

                genrePlaceholder,
                artistPlaceholder,
                albumPlaceholder
            )

            if (loader.response == MusicLoaderResponse.DONE) {
                // If the loading succeeds, then sort the songs and update the value
                val sorter = MusicSorter(
                    loader.genres,
                    loader.artists,
                    loader.albums,
                    loader.songs,

                    genrePlaceholder,
                    artistPlaceholder,
                    albumPlaceholder
                )

                mSongs = sorter.songs.toList()
                mAlbums = sorter.albums.toList()
                mArtists = sorter.artists.toList()
                mGenres = sorter.genres.toList()

                val elapsed = System.currentTimeMillis() - start

                this@MusicStore.logD("Music load completed successfully in ${elapsed}ms.")

                loaded = true
            }

            loader.response
        }
    }

    /**
     * Get a list of data for a [DisplayMode].
     * @param displayMode The [DisplayMode] given
     * @return A list of [BaseModel]s for that [DisplayMode]
     */
    fun getListForDisplayMode(displayMode: DisplayMode): List<BaseModel> {
        return when (displayMode) {
            DisplayMode.SHOW_GENRES -> mGenres
            DisplayMode.SHOW_ARTISTS -> mArtists
            DisplayMode.SHOW_ALBUMS -> mAlbums
            DisplayMode.SHOW_SONGS -> mSongs
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: MusicStore? = null

        /**
         * Get/Instantiate the single instance of [MusicStore].
         */
        fun getInstance(): MusicStore {
            val currentInstance = INSTANCE

            if (currentInstance != null) {
                return currentInstance
            }

            synchronized(this) {
                val newInstance = MusicStore()
                INSTANCE = newInstance
                return newInstance
            }
        }
    }
}
