package org.oxycblt.auxio.playback.state

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.database.PlaybackState
import org.oxycblt.auxio.database.PlaybackStateDatabase
import org.oxycblt.auxio.database.QueueItem
import org.oxycblt.auxio.logD
import org.oxycblt.auxio.logE
import org.oxycblt.auxio.music.Album
import org.oxycblt.auxio.music.Artist
import org.oxycblt.auxio.music.BaseModel
import org.oxycblt.auxio.music.Genre
import org.oxycblt.auxio.music.Header
import org.oxycblt.auxio.music.MusicStore
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.settings.SettingsManager
import kotlin.random.Random

/**
 * Master class for the playback state. This should ***not*** be used outside of the playback module.
 * - If you want to use the playback state in the UI, use [org.oxycblt.auxio.playback.PlaybackViewModel].
 * - If you want to use the playback state with the ExoPlayer instance or system-side things,
 * use [org.oxycblt.auxio.playback.PlaybackService].
 *
 * All access should be done with [PlaybackStateManager.getInstance].
 * @author OxygenCobalt
 */
class PlaybackStateManager private constructor() {
    // Playback
    private var mSong: Song? = null
        set(value) {
            field = value
            callbacks.forEach { it.onSongUpdate(value) }
        }
    private var mPosition: Long = 0
        set(value) {
            field = value
            callbacks.forEach { it.onPositionUpdate(value) }
        }
    private var mParent: BaseModel? = null
        set(value) {
            field = value
            callbacks.forEach { it.onParentUpdate(value) }
        }

    // Queue
    private var mQueue = mutableListOf<Song>()
        set(value) {
            field = value
            callbacks.forEach { it.onQueueUpdate(value) }
        }
    private var mUserQueue = mutableListOf<Song>()
        set(value) {
            field = value
            callbacks.forEach { it.onUserQueueUpdate(value) }
        }
    private var mIndex = 0
        set(value) {
            field = value
            callbacks.forEach { it.onIndexUpdate(value) }
        }
    private var mMode = PlaybackMode.ALL_SONGS
        set(value) {
            field = value
            callbacks.forEach { it.onModeUpdate(value) }
        }

    // Status
    private var mIsPlaying = false
        set(value) {
            field = value
            callbacks.forEach { it.onPlayingUpdate(value) }
        }

    private var mIsShuffling = false
        set(value) {
            field = value
            callbacks.forEach { it.onShuffleUpdate(value) }
        }
    private var mLoopMode = LoopMode.NONE
        set(value) {
            field = value
            callbacks.forEach { it.onLoopUpdate(value) }
        }
    private var mIsInUserQueue = false
    private var mIsRestored = false
    private var mHasPlayed = false
    private var mShuffleSeed = -1L

    /** The currently playing song. Null if there isn't one */
    val song: Song? get() = mSong
    /** The parent the queue is based on, null if all_songs */
    val parent: BaseModel? get() = mParent
    /** The current playback progress */
    val position: Long get() = mPosition
    /** The current queue determined by [parent] and [mode] */
    val queue: MutableList<Song> get() = mQueue
    /** The queue created by the user. */
    val userQueue: MutableList<Song> get() = mUserQueue
    /** The current index of the queue */
    val index: Int get() = mIndex
    /** The current [PlaybackMode] */
    val mode: PlaybackMode get() = mMode
    /** Whether playback is paused or not */
    val isPlaying: Boolean get() = mIsPlaying
    /** Whether the queue is shuffled */
    val isShuffling: Boolean get() = mIsShuffling
    /** The current [LoopMode] */
    val loopMode: LoopMode get() = mLoopMode
    /** Whether this instance has already been restored */
    val isRestored: Boolean get() = mIsRestored
    /** Whether this instance has started playing or not */
    val hasPlayed: Boolean get() = mHasPlayed

    private val settingsManager = SettingsManager.getInstance()

    // --- CALLBACKS ---

    private val callbacks = mutableListOf<Callback>()

    /**
     * Add a [PlaybackStateManager.Callback] to this instance.
     * Make sure to remove the callback with [removeCallback] when done.
     */
    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }

    /**
     * Remove a [PlaybackStateManager.Callback] bound to this instance.
     */
    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    // --- PLAYING FUNCTIONS ---

    /**
     * Play a song.
     * @param song The song to be played
     * @param mode The [PlaybackMode] to construct the queue off of.
     */
    fun playSong(song: Song, mode: PlaybackMode) {
        // Auxio doesn't support playing songs while swapping the mode to GENRE, as its impossible
        // to determine what genre a song has.
        if (mode == PlaybackMode.IN_GENRE) {
            logE("Auxio cant play songs with the mode of IN_GENRE.")

            return
        }

        logD("Updating song to ${song.name} and mode to $mode")

        val musicStore = MusicStore.getInstance()

        when (mode) {
            PlaybackMode.ALL_SONGS -> {
                mParent = null
                mQueue = musicStore.songs.toMutableList()
            }

            PlaybackMode.IN_ARTIST -> {
                mParent = song.album.artist
                mQueue = song.album.artist.songs.toMutableList()
            }

            PlaybackMode.IN_ALBUM -> {
                mParent = song.album
                mQueue = song.album.songs
            }

            else -> {
            }
        }

        mMode = mode

        resetLoopMode()
        updatePlayback(song)

        if (settingsManager.keepShuffle) {
            if (mIsShuffling) {
                genShuffle(true)
            } else {
                resetShuffle()
            }
        } else {
            setShuffleStatus(false)
        }

        mIndex = mQueue.indexOf(song)
    }

    /**
     * Play a parent model, e.g an artist or an album.
     * @param baseModel The model to use
     * @param shuffled Whether to shuffle the queue or not
     */
    fun playParentModel(baseModel: BaseModel, shuffled: Boolean) {
        if (baseModel is Song || baseModel is Header) {
            // This should never occur.
            logE("playParentModel does not support ${baseModel::class.simpleName}.")

            return
        }

        logD("Playing ${baseModel.name}")

        mParent = baseModel
        mIndex = 0
        mIsShuffling = shuffled

        when (baseModel) {
            is Album -> {
                mQueue = orderSongsInAlbum(baseModel)
                mMode = PlaybackMode.IN_ALBUM
            }
            is Artist -> {
                mQueue = orderSongsInArtist(baseModel)
                mMode = PlaybackMode.IN_ARTIST
            }
            is Genre -> {
                mQueue = orderSongsInGenre(baseModel)
                mMode = PlaybackMode.IN_GENRE
            }

            else -> {
            }
        }

        resetLoopMode()

        updatePlayback(mQueue[0])

        if (mIsShuffling) {
            genShuffle(false)
        } else {
            resetShuffle()
        }
    }

    /**
     * Shortcut function for updating what song is being played. ***USE THIS INSTEAD OF WRITING OUT ALL THE CODE YOURSELF!!!***
     * @param song The song to play
     * @param dontPlay (Optional, defaults to false) whether to not set [isPlaying] to true.
     */
    private fun updatePlayback(song: Song, dontPlay: Boolean = false) {
        mSong = song
        mPosition = 0

        if (!mIsPlaying && !dontPlay) {
            setPlayingStatus(true)
        }

        mIsInUserQueue = false
    }

    /**
     * Update the current position. Will not notify any listeners of a seek event, that's what [seekTo] is for.
     * @param position The new position in millis.
     */
    fun setPosition(position: Long) {
        mSong?.let {
            // Don't accept any bugged positions that are over the duration of the song.
            if (position <= it.duration) {
                mPosition = position
            }
        }
    }

    /**
     * **Seek** to a position, this calls [PlaybackStateManager.Callback.onSeekConfirm] to notify
     * elements that rely on that.
     * @param position The position to seek to in millis.
     */
    fun seekTo(position: Long) {
        mPosition = position

        callbacks.forEach { it.onSeekConfirm(position) }
    }

    // --- QUEUE FUNCTIONS ---

    /**
     * Go to the next song, along with doing all the checks that entails.
     */
    fun next() {
        resetLoopMode()

        // If there's anything in the user queue, go to the first song in there instead
        // of incrementing the index.
        if (mUserQueue.isNotEmpty()) {
            updatePlayback(mUserQueue[0])
            mUserQueue.removeAt(0)

            // Mark that the playback state is currently in the user queue, for later.
            mIsInUserQueue = true

            forceUserQueueUpdate()
        } else {
            // Increment the index.
            // If it cant be incremented anymore, end playback or loop depending on the setting.
            if (mIndex < mQueue.lastIndex) {
                mIndex = mIndex.inc()
            } else {
                handlePlaylistEnd()

                return
            }

            updatePlayback(mQueue[mIndex])
            forceQueueUpdate()
        }
    }

    /**
     * Go to the previous song, doing any checks that are needed.
     */
    fun prev() {
        // If enabled, rewind before skipping back if the position is past the threshold set.
        if (settingsManager.rewindWithPrev && mPosition >= settingsManager.rewindThreshold) {
            seekTo(0)
        } else {
            // Only decrement the index if there's a song to move back to AND if we are not exiting
            // the user queue.
            if (mIndex > 0 && !mIsInUserQueue) {
                mIndex = mIndex.dec()
            }

            resetLoopMode()

            updatePlayback(mQueue[mIndex])

            forceQueueUpdate()
        }
    }

    /**
     * Handle what to do at then end of a playlist.
     */
    private fun handlePlaylistEnd() {
        when (settingsManager.doAtEnd) {
            SettingsManager.EntryValues.AT_END_LOOP_PAUSE -> {
                mIndex = 0
                forceQueueUpdate()

                updatePlayback(mQueue[0], dontPlay = true)
                setPlayingStatus(false)
            }

            SettingsManager.EntryValues.AT_END_LOOP -> {
                mIndex = 0
                forceQueueUpdate()

                updatePlayback(mQueue[0])
            }

            SettingsManager.EntryValues.AT_END_STOP -> {
                mQueue.clear()
                forceQueueUpdate()

                mSong = null
            }
        }
    }

    // --- QUEUE EDITING FUNCTIONS ---

    /**
     * Remove a queue item at a QUEUE index. Will log an error if the index is out of bounds
     * @param index The index at which the item should be removed.
     */
    fun removeQueueItem(index: Int): Boolean {
        logD("Removing item ${mQueue[index].name}.")

        if (index > mQueue.size || index < 0) {
            logE("Index is out of bounds, did not remove queue item.")

            return false
        }

        mQueue.removeAt(index)

        forceQueueUpdate()

        return true
    }

    /**
     * Move a queue item from a QUEUE INDEX to a QUEUE INDEX. Will log an error if one of the indices
     * is out of bounds.
     * @param from The starting item's index
     * @param to The destination index.
     */
    fun moveQueueItems(from: Int, to: Int): Boolean {
        try {
            val item = mQueue.removeAt(from)
            mQueue.add(to, item)
        } catch (exception: IndexOutOfBoundsException) {
            logE("Indices were out of bounds, did not move queue item")

            return false
        }

        forceQueueUpdate()

        return true
    }

    /**
     * Add a song to the user queue.
     * @param song The song to add
     */
    fun addToUserQueue(song: Song) {
        mUserQueue.add(song)

        forceUserQueueUpdate()
    }

    /**
     * Add a list of songs to the user queue.
     * @param songs The songs to add.
     */
    fun addToUserQueue(songs: List<Song>) {
        mUserQueue.addAll(songs)

        forceUserQueueUpdate()
    }

    /**
     * Remove a USER QUEUE item at a USER QUEUE index. Will log an error if the index is out of bounds.
     * @param index The index at which the item should be removed.
     */
    fun removeUserQueueItem(index: Int) {
        logD("Removing item ${mUserQueue[index].name}.")

        if (index > mUserQueue.size || index < 0) {
            logE("Index is out of bounds, did not remove queue item.")

            return
        }

        mUserQueue.removeAt(index)

        forceUserQueueUpdate()
    }

    /**
     * Move a USER QUEUE item from a USER QUEUE index to another USER QUEUE index. Will log an error if one of the indices
     * is out of bounds.
     * @param from The starting item's index
     * @param to The destination index.
     */
    fun moveUserQueueItems(from: Int, to: Int) {
        try {
            val item = mUserQueue.removeAt(from)
            mUserQueue.add(to, item)
        } catch (exception: IndexOutOfBoundsException) {
            logE("Indices were out of bounds, did not move queue item")

            return
        }

        forceUserQueueUpdate()
    }

    /**
     * Clear the user queue. Forces a user queue update.
     */
    fun clearUserQueue() {
        mUserQueue.clear()

        forceUserQueueUpdate()
    }

    /**
     * Force any callbacks to receive a queue update.
     */
    private fun forceQueueUpdate() {
        mQueue = mQueue
    }

    /**
     * Force any callbacks to recieve a user queue update.
     */
    private fun forceUserQueueUpdate() {
        mUserQueue = mUserQueue
    }

    // --- SHUFFLE FUNCTIONS ---

    /**
     * Shuffle all songs.
     */
    fun shuffleAll() {
        val musicStore = MusicStore.getInstance()

        mIsShuffling = true
        mQueue = musicStore.songs.toMutableList()
        mMode = PlaybackMode.ALL_SONGS
        mIndex = 0

        genShuffle(false)
        updatePlayback(mQueue[0])
    }

    /**
     * Generate a new shuffled queue.
     * @param keepSong Whether to keep the currently playing song or to dispose of it
     * @param useLastSong (Optional, defaults to false) Whether to use the previous song for the index calculations caused by the above parameter.
     */
    private fun genShuffle(keepSong: Boolean, useLastSong: Boolean = false) {
        val newSeed = Random.Default.nextLong()

        logD("Shuffling queue with seed $newSeed")

        val lastSong = if (useLastSong) mQueue[mIndex] else mSong

        mShuffleSeed = newSeed

        mQueue.shuffle(Random(newSeed))
        mIndex = 0

        // If specified, make the current song the first member of the queue.
        if (keepSong) {
            moveQueueItems(mQueue.indexOf(lastSong), 0)
        } else {
            // Otherwise, just start from the zeroth position in the queue.
            mSong = mQueue[0]
        }

        forceQueueUpdate()
    }

    /**
     * Reset the queue to its normal, ordered state.
     * @param useLastSong (Optional, defaults to false) Whether to use the previous song for the index calculations.
     */
    private fun resetShuffle(useLastSong: Boolean = false) {
        mShuffleSeed = -1L

        val lastSong = if (useLastSong) mQueue[mIndex] else mSong

        setupOrderedQueue()

        mIndex = mQueue.indexOf(lastSong)

        forceQueueUpdate()
    }

    // --- STATE FUNCTIONS ---

    /**
     * Set the current playing status
     * @param value Whether the playback should be playing or paused.
     */
    fun setPlayingStatus(value: Boolean) {
        if (mIsPlaying != value) {
            if (value) {
                mHasPlayed = true
            }

            mIsPlaying = value
        }
    }

    /**
     * Set the shuffle status. Updates the queue accordingly
     * @param value Whether the queue should be shuffled or not.
     */
    fun setShuffleStatus(value: Boolean) {
        mIsShuffling = value

        if (mIsShuffling) {
            genShuffle(true, mIsInUserQueue)
        } else {
            resetShuffle(mIsInUserQueue)
        }
    }

    /**
     * Set the [LoopMode]
     * @param value The [LoopMode] to be used
     */
    fun setLoopMode(mode: LoopMode) {
        mLoopMode = mode
    }

    /**
     * Reset the has played status as if this instance is fresh.
     */
    fun resetHasPlayedStatus() {
        mHasPlayed = false
    }

    /**
     * Reset the current [LoopMode], if needed.
     * Use this instead of duplicating the code manually.
     */
    private fun resetLoopMode() {
        // Reset the loop mode from ONCE if needed.
        if (mLoopMode == LoopMode.ONCE) {
            mLoopMode = LoopMode.NONE
        }
    }

    // --- PERSISTENCE FUNCTIONS ---

    /**
     * Save the current state to the database.
     * @param context [Context] required
     */
    suspend fun saveStateToDatabase(context: Context) {
        logD("Saving state to DB.")

        val start = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            val playbackState = packToPlaybackState()
            val queueItems = packQueue()

            val database = PlaybackStateDatabase.getInstance(context)
            database.writeState(playbackState)
            database.writeQueue(queueItems)
        }

        val time = System.currentTimeMillis() - start

        logD("Save finished in ${time}ms")
    }

    /**
     * Restore the state from the database
     * @param context [Context] required.
     */
    suspend fun getStateFromDatabase(context: Context) {
        logD("Getting state from DB.")

        val start = System.currentTimeMillis()

        val state: PlaybackState?

        val queueItems = withContext(Dispatchers.IO) {
            val database = PlaybackStateDatabase.getInstance(context)

            state = database.readState()
            database.readQueue()
        }

        val loadTime = System.currentTimeMillis() - start

        logD("Load finished in ${loadTime}ms")

        state?.let {
            logD("Valid playback state $it")
            logD("Valid queue size ${queueItems.size}")

            unpackFromPlaybackState(it)
            unpackQueue(queueItems)
            doParentSanityCheck()
        }

        val time = System.currentTimeMillis() - start

        logD("Restore finished in ${time}ms")

        mIsRestored = true
    }

    /**
     * Back the current state into a [PlaybackState] to be saved.
     * @return A [PlaybackState] reflecting the current state.
     */
    private fun packToPlaybackState(): PlaybackState {
        val songId = mSong?.id ?: -1L
        val parentId = mParent?.id ?: -1L
        val intMode = mMode.toInt()
        val intLoopMode = mLoopMode.toInt()

        return PlaybackState(
            songId = songId,
            position = mPosition,
            parentId = parentId,
            index = mIndex,
            mode = intMode,
            isShuffling = mIsShuffling,
            loopMode = intLoopMode,
            inUserQueue = mIsInUserQueue
        )
    }

    /**
     * Pack the queue into a list of [QueueItem]s to be saved.
     * @return A list of packed queue items.
     */
    private fun packQueue(): List<QueueItem> {
        val unified = mutableListOf<QueueItem>()

        var queueItemId = 0L

        mUserQueue.forEach {
            unified.add(QueueItem(queueItemId, it.id, it.albumId, true))
            queueItemId++
        }

        mQueue.forEach {
            unified.add(QueueItem(queueItemId, it.id, it.albumId, false))
            queueItemId++
        }

        return unified
    }

    /**
     * Unpack the state from a [PlaybackState]
     * @param playbackState The state to unpack.
     */
    private fun unpackFromPlaybackState(playbackState: PlaybackState) {
        val musicStore = MusicStore.getInstance()

        // Turn the simplified information from PlaybackState into values that can be used
        mSong = musicStore.songs.find { it.id == playbackState.songId }
        mPosition = playbackState.position
        mParent = musicStore.parents.find { it.id == playbackState.parentId }
        mMode = PlaybackMode.fromInt(playbackState.mode) ?: PlaybackMode.ALL_SONGS
        mLoopMode = LoopMode.fromInt(playbackState.loopMode) ?: LoopMode.NONE
        mIsShuffling = playbackState.isShuffling
        mIsInUserQueue = playbackState.inUserQueue
        mIndex = playbackState.index

        callbacks.forEach {
            it.onSeekConfirm(mPosition)
            it.onModeUpdate(mMode)
            it.onRestoreFinish()
        }
    }

    /**
     * Unpack a list of queue items into a queue & user queue.
     * @param queueItems The list of [QueueItem]s to unpack.
     */
    private fun unpackQueue(queueItems: List<QueueItem>) {
        val musicStore = MusicStore.getInstance()

        queueItems.forEach { item ->
            // Traverse albums and then album songs instead of just the songs, as its faster.
            musicStore.albums.find { it.id == item.albumId }
                ?.songs?.find { it.id == item.songId }?.let {
                if (item.isUserQueue) {
                    mUserQueue.add(it)
                } else {
                    mQueue.add(it)
                }
            }
        }

        // When done, get a more accurate index to prevent issues with queue songs that were saved
        // to the db but are now deleted when the restore occurred.
        if (!mIsInUserQueue) {
            mSong?.let {
                val index = mQueue.indexOf(it)
                mIndex = if (index != -1) index else mIndex
            }
        }

        forceQueueUpdate()
        forceUserQueueUpdate()
    }

    /**
     * Do the sanity check to make sure the parent was not lost in the restore process.
     */
    private fun doParentSanityCheck() {
        // Check if the parent was lost while in the DB.
        if (mSong != null && mParent == null && mMode != PlaybackMode.ALL_SONGS) {
            logD("Parent lost, attempting restore.")

            mParent = when (mMode) {
                PlaybackMode.IN_ALBUM -> mQueue.firstOrNull()?.album
                PlaybackMode.IN_ARTIST -> mQueue.firstOrNull()?.album?.artist
                PlaybackMode.IN_GENRE -> getCommonGenre()
                PlaybackMode.ALL_SONGS -> null
            }
        }
    }

    /**
     * Search for the common genre out of a queue of songs that **should have a common genre**.
     * @return The **single** common genre, null if there isn't any or if there's multiple.
     */
    private fun getCommonGenre(): Genre? {
        // Pool of "Possible" genres, these get narrowed down until the list is only
        // the actual genre(s) that all songs in the queue have in common.
        var genres = mutableListOf<Genre>()
        var otherGenres: MutableList<Genre>

        for (queueSong in mQueue) {
            // If there's still songs to check despite the pool of genres being empty, re-add them.
            if (genres.size == 0) {
                genres.addAll(queueSong.album.artist.genres)
                continue
            }

            otherGenres = genres.toMutableList()

            // Iterate through the current genres and remove the ones that don't exist in this song,
            // narrowing down the pool of possible genres.
            for (genre in genres) {
                if (queueSong.album.artist.genres.find { it.id == genre.id } == null) {
                    otherGenres.remove(genre)
                }
            }

            genres = otherGenres.toMutableList()
        }

        logD("Found genre $genres")

        // There should not be more than one common genre, so return null if that's the case
        if (genres.size > 1) {
            return null
        }

        // Sometimes the narrowing process will lead to a zero-size list, so return null if that
        // is the case.
        return genres.firstOrNull()
    }

    // --- ORDERING FUNCTIONS ---

    /**
     * Set up an ordered queue.
     */
    private fun setupOrderedQueue() {
        mQueue = when (mMode) {
            PlaybackMode.IN_ARTIST -> orderSongsInArtist(mParent as Artist)
            PlaybackMode.IN_ALBUM -> orderSongsInAlbum(mParent as Album)
            PlaybackMode.IN_GENRE -> orderSongsInGenre(mParent as Genre)
            PlaybackMode.ALL_SONGS -> MusicStore.getInstance().songs.toMutableList()
        }
    }

    /**
     * Create an ordered queue based on an [Album].
     */
    private fun orderSongsInAlbum(album: Album): MutableList<Song> {
        return album.songs.sortedBy { it.track }.toMutableList()
    }

    /**
     * Create an ordered queue based on an [Artist].
     * @return A list of the songs in the [Artist], ordered.
     */
    private fun orderSongsInArtist(artist: Artist): MutableList<Song> {
        val final = mutableListOf<Song>()

        artist.albums.sortedByDescending { it.year }.forEach { album ->
            final.addAll(album.songs.sortedBy { it.track })
        }

        return final
    }

    /**
     * Create an ordered queue based on a [Genre].
     * @return A list of the songs in the [Genre], ordered.
     */
    private fun orderSongsInGenre(genre: Genre): MutableList<Song> {
        val final = mutableListOf<Song>()

        genre.artists.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        ).forEach { artist ->
            artist.albums.sortedByDescending { it.year }.forEach { album ->
                final.addAll(album.songs.sortedBy { it.track })
            }
        }

        return final
    }

    /**
     * The interface for receiving updates from [PlaybackStateManager].
     * Add the callback to [PlaybackStateManager] using [addCallback],
     * remove them on destruction with [removeCallback].
     */
    interface Callback {
        /** Called when the song updates */
        fun onSongUpdate(song: Song?) {}
        /** Called when the parent updates */
        fun onParentUpdate(parent: BaseModel?) {}
        /** Called when the position updates */
        fun onPositionUpdate(position: Long) {}
        /** Called when the queue updates */
        fun onQueueUpdate(queue: MutableList<Song>) {}
        /** Called when the user queue updates */
        fun onUserQueueUpdate(userQueue: MutableList<Song>) {}
        /** Called when the mode updates */
        fun onModeUpdate(mode: PlaybackMode) {}
        /** Called when the index updates */
        fun onIndexUpdate(index: Int) {}
        /** Called when the playing status changes */
        fun onPlayingUpdate(isPlaying: Boolean) {}
        /** Called when the shuffle status changes */
        fun onShuffleUpdate(isShuffling: Boolean) {}
        /** Called when the loop mode changes */
        fun onLoopUpdate(mode: LoopMode) {}
        /** Called when a seek is confirmed */
        fun onSeekConfirm(position: Long) {}
        /** Called when the restore process is finished */
        fun onRestoreFinish() {}
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaybackStateManager? = null

        /**
         * Get/Instantiate the single instance of [PlaybackStateManager].
         */
        fun getInstance(): PlaybackStateManager {
            val currentInstance = INSTANCE

            if (currentInstance != null) {
                return currentInstance
            }

            synchronized(this) {
                val newInstance = PlaybackStateManager()
                INSTANCE = newInstance
                return newInstance
            }
        }
    }
}
