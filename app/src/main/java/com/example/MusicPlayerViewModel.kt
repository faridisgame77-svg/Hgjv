package com.example

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MusicPlayerViewModel : ViewModel() {

    companion object {
        @Volatile
        var instance: MusicPlayerViewModel? = null
    }

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _totalDuration = MutableStateFlow(1L)
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    private var isAutoPlayPerformed = false
    private val PREFS_NAME = "music_player_settings"
    private val KEY_AUTO_PLAY = "auto_play_on_launch"
    private val KEY_FAVORITES = "favorite_song_ids"
    private val KEY_SOUND_PRESET = "selected_sound_preset"
    private val KEY_VISUALIZER_STYLE = "selected_visualizer_style"
    private val KEY_THEME = "selected_theme_accent"
    private val KEY_LANGUAGE = "selected_system_language"

    private val _isAutoPlayEnabled = MutableStateFlow(true)
    val isAutoPlayEnabled: StateFlow<Boolean> = _isAutoPlayEnabled.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _soundPreset = MutableStateFlow("Normal")
    val soundPreset: StateFlow<String> = _soundPreset.asStateFlow()

    private val _visualizerStyle = MutableStateFlow("Glow Pillars")
    val visualizerStyle: StateFlow<String> = _visualizerStyle.asStateFlow()

    private val _accentColorTheme = MutableStateFlow("Aura")
    val accentColorTheme: StateFlow<String> = _accentColorTheme.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("AZ")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // ────────────────────────────────────────────────────────────────────────────────
    // PREMIUM AIRPODS & BLUETOOTH INTEGRATION STATES (REAL & SIMULATED)
    // ────────────────────────────────────────────────────────────────────────────────
    private val _isBluetoothAudioActive = MutableStateFlow(false)
    val isBluetoothAudioActive: StateFlow<Boolean> = _isBluetoothAudioActive.asStateFlow()

    private val _isAirPodsSimulatedEnabled = MutableStateFlow(false)
    val isAirPodsSimulatedEnabled: StateFlow<Boolean> = _isAirPodsSimulatedEnabled.asStateFlow()

    private val _selectedAirPodsModel = MutableStateFlow("AirPods Pro")
    val selectedAirPodsModel: StateFlow<String> = _selectedAirPodsModel.asStateFlow()

    private val _airpodsLeftBattery = MutableStateFlow(88)
    val airpodsLeftBattery: StateFlow<Int> = _airpodsLeftBattery.asStateFlow()

    private val _airpodsRightBattery = MutableStateFlow(92)
    val airpodsRightBattery: StateFlow<Int> = _airpodsRightBattery.asStateFlow()

    private val _airpodsCaseBattery = MutableStateFlow(100)
    val airpodsCaseBattery: StateFlow<Int> = _airpodsCaseBattery.asStateFlow()

    private val _airpodsLeftCharging = MutableStateFlow(false)
    val airpodsLeftCharging: StateFlow<Boolean> = _airpodsLeftCharging.asStateFlow()

    private val _airpodsRightCharging = MutableStateFlow(false)
    val airpodsRightCharging: StateFlow<Boolean> = _airpodsRightCharging.asStateFlow()

    private val _airpodsCaseCharging = MutableStateFlow(true)
    val airpodsCaseCharging: StateFlow<Boolean> = _airpodsCaseCharging.asStateFlow()

    private val _noiseCancellationMode = MutableStateFlow("Transparency")
    val noiseCancellationMode: StateFlow<String> = _noiseCancellationMode.asStateFlow()

    private val _spatialAudioMode = MutableStateFlow("Fixed")
    val spatialAudioMode: StateFlow<String> = _spatialAudioMode.asStateFlow()

    private val _systemVolume = MutableStateFlow(70)
    val systemVolume: StateFlow<Int> = _systemVolume.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    init {
        instance = this
        // Initialize simple clean MediaPlayer
        mediaPlayer = MediaPlayer()
    }

    fun checkBluetoothStatus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        audioManager?.let { manager ->
            @Suppress("DEPRECATION")
            _isBluetoothAudioActive.value = manager.isBluetoothA2dpOn || manager.isBluetoothScoOn
        }
    }

    fun setAirPodsSimulationEnabled(enabled: Boolean) {
        _isAirPodsSimulatedEnabled.value = enabled
    }

    fun setSelectedAirPodsModel(model: String) {
        _selectedAirPodsModel.value = model
        // Change battery status to make the interaction feel organic and fun when Model changes
        when (model) {
            "AirPods Pro" -> {
                _airpodsLeftBattery.value = 85
                _airpodsRightBattery.value = 85
                _airpodsCaseBattery.value = 95
            }
            "AirPods Max" -> {
                _airpodsLeftBattery.value = 98
                _airpodsRightBattery.value = 98
                _airpodsCaseBattery.value = 0 // AirPods Max does not have a separate charging case percentage
            }
            "AirPods 3" -> {
                _airpodsLeftBattery.value = 72
                _airpodsRightBattery.value = 80
                _airpodsCaseBattery.value = 45
            }
            "Sony WH-1000XM4" -> {
                _airpodsLeftBattery.value = 90
                _airpodsRightBattery.value = 90
                _airpodsCaseBattery.value = 0
            }
        }
    }

    fun setAirPodsBatteries(left: Int, right: Int, case: Int) {
        _airpodsLeftBattery.value = left.coerceIn(0, 100)
        _airpodsRightBattery.value = right.coerceIn(0, 100)
        _airpodsCaseBattery.value = case.coerceIn(0, 100)
    }

    fun toggleAirPodsCharging(left: Boolean, right: Boolean, case: Boolean) {
        _airpodsLeftCharging.value = left
        _airpodsRightCharging.value = right
        _airpodsCaseCharging.value = case
    }

    fun setNoiseCancellationMode(mode: String) {
        _noiseCancellationMode.value = mode
    }

    fun setSpatialAudioMode(mode: String) {
        _spatialAudioMode.value = mode
    }

    fun updateVolumeFromSystem(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        audioManager?.let { manager ->
            val maxVol = manager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVol = manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                val percent = (currentVol * 100) / maxVol
                if (_systemVolume.value != percent) {
                    _systemVolume.value = percent
                    updateAllWidgetsCombined(context)
                }
            }
        }
    }

    fun setSystemVolume(context: Context, percentage: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        audioManager?.let { manager ->
            val maxVol = manager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val target = (percentage * maxVol) / 100
            try {
                manager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target.coerceIn(0, maxVol), 0)
                _systemVolume.value = percentage
            } catch (e: Exception) {
                Log.e("MusicPlayerVM", "Səs səviyyəsi təyin edilərkən xəta: ", e)
                _systemVolume.value = percentage
            }
            updateAllWidgetsCombined(context)
        }
    }

    fun sendLocateBeep(context: Context) {
        // High quality sound chime alert to find AirPods
        viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val durationSeconds = 1
            val numSamples = sampleRate * durationSeconds
            val headerAndDataSize = 44 + numSamples * 2
            val header = ByteArray(headerAndDataSize)
            
            // Standard WAV Header
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            val fileLen = headerAndDataSize - 8
            header[4] = (fileLen and 0xff).toByte()
            header[5] = ((fileLen shr 8) and 0xff).toByte()
            header[6] = ((fileLen shr 16) and 0xff).toByte()
            header[7] = ((fileLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Chunk size 16
            header[20] = 1; header[21] = 0 // PCM=1
            header[22] = 1; header[23] = 0 // Mono=1
            header[24] = (sampleRate and 0xff).toByte(); header[25] = ((sampleRate shr 8) and 0xff).toByte(); header[26] = ((sampleRate shr 16) and 0xff).toByte(); header[27] = ((sampleRate shr 24) and 0xff).toByte()
            val byteRate = sampleRate * 2
            header[28] = (byteRate and 0xff).toByte(); header[29] = ((byteRate shr 8) and 0xff).toByte(); header[30] = ((byteRate shr 16) and 0xff).toByte(); header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2; header[33] = 0 // Block align
            header[34] = 16; header[35] = 0 // Bits per sample
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            val dataSize = numSamples * 2
            header[40] = (dataSize and 0xff).toByte(); header[41] = ((dataSize shr 8) and 0xff).toByte(); header[42] = ((dataSize shr 16) and 0xff).toByte(); header[43] = ((dataSize shr 24) and 0xff).toByte()

            // High frequency alert sinewave
            val freq = 2000.0 // 2 kHz prominent beep
            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * freq * i / sampleRate
                val rawValue = Math.sin(angle)
                // fade out and in pulsating chime
                val pulse = Math.sin(2.0 * Math.PI * 4.0 * i / sampleRate) // 4 Hz pulse
                val sVal = (rawValue * (pulse + 1.0) * 0.4 * 32767.0).toInt().coerceIn(-32768, 32767)
                val pos = 44 + i * 2
                header[pos] = (sVal and 0xff).toByte()
                header[pos + 1] = ((sVal shr 8) and 0xff).toByte()
            }

            withContext(Dispatchers.Main) {
                try {
                    val beepPlayer = MediaPlayer()
                    val tempFile = File(context.cacheDir, "locate_beep.wav")
                    tempFile.writeBytes(header)
                    beepPlayer.setDataSource(context, Uri.fromFile(tempFile))
                    beepPlayer.prepare()
                    beepPlayer.start()
                    // release after playback completes
                    beepPlayer.setOnCompletionListener {
                        it.release()
                        try { tempFile.delete() } catch (ignored: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlayerVM", "Beep səs fəaliyyətində xəta: ", e)
                }
            }
        }
    }

    fun setSelectedLanguage(context: Context, langCode: String) {
        _selectedLanguage.value = langCode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply()
    }

    fun setAutoPlayEnabled(context: Context, enabled: Boolean) {
        _isAutoPlayEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_PLAY, enabled).apply()
    }

    fun toggleFavorite(context: Context, songId: Long) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(songId)) {
            current.remove(songId)
        } else {
            current.add(songId)
        }
        _favorites.value = current
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_FAVORITES, current.map { it.toString() }.toSet()).apply()
    }

    fun setSoundPreset(context: Context, preset: String) {
        _soundPreset.value = preset
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SOUND_PRESET, preset).apply()
    }

    fun setVisualizerStyle(context: Context, style: String) {
        _visualizerStyle.value = style
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_VISUALIZER_STYLE, style).apply()
    }

    fun setAccentColorTheme(context: Context, theme: String) {
        _accentColorTheme.value = theme
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun scanSongs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isAutoPlayEnabled.value = prefs.getBoolean(KEY_AUTO_PLAY, true)
        
        // Load favorites
        val savedFavs = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        _favorites.value = savedFavs.mapNotNull { it.toLongOrNull() }.toSet()

        // Load premium sound presets, visualizer styles, dynamic themes, and language
        _soundPreset.value = prefs.getString(KEY_SOUND_PRESET, "Normal") ?: "Normal"
        _visualizerStyle.value = prefs.getString(KEY_VISUALIZER_STYLE, "Glow Pillars") ?: "Glow Pillars"
        _accentColorTheme.value = prefs.getString(KEY_THEME, "Aura") ?: "Aura"
        _selectedLanguage.value = prefs.getString(KEY_LANGUAGE, "AZ") ?: "AZ"

        if (_isScanning.value) return
        _isScanning.value = true
        _scanMessage.value = LanguageManager.get("scanning_storage", _selectedLanguage.value)

        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Song>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
            )
            // Query only music files
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            try {
                context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: LanguageManager.get("unknown_song", _selectedLanguage.value)
                        val artist = cursor.getString(artistCol) ?: LanguageManager.get("unknown_artist", _selectedLanguage.value)
                        val album = cursor.getString(albumCol) ?: LanguageManager.get("unknown_album", _selectedLanguage.value)
                        val duration = cursor.getLong(durationCol)
                        val size = cursor.getLong(sizeCol)
                        val songUri = Uri.withAppendedPath(uri, id.toString())

                        // Skip files that have 0 or negative duration
                        if (duration > 0) {
                            list.add(
                                Song(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    duration = duration,
                                    uriString = songUri.toString(),
                                    size = size,
                                    isDemo = false
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerVM", "Disk skan olunarkən xəta: ", e)
            }

            withContext(Dispatchers.Main) {
                _songs.value = list
                _isScanning.value = false
                if (list.isEmpty()) {
                    _scanMessage.value = LanguageManager.get("no_songs_desc", _selectedLanguage.value)
                } else {
                    val compMsg = LanguageManager.get("scanning_completed", _selectedLanguage.value)
                    _scanMessage.value = "${list.size} $compMsg"
                    if (_isAutoPlayEnabled.value && !isAutoPlayPerformed && _currentSong.value == null) {
                        isAutoPlayPerformed = true
                        val randomSong = list.random()
                        playSong(context, randomSong)
                    }
                }
            }
        }
    }

    fun playSong(context: Context, song: Song) {
        viewModelScope.launch {
            try {
                // Reset standard playback position
                stopProgressUpdate()
                _playbackPosition.value = 0L
                _totalDuration.value = if (song.duration > 0) song.duration else 1L
                
                mediaPlayer?.let { player ->
                    player.reset()
                    player.setDataSource(context, Uri.parse(song.uriString))
                    player.prepare()
                    player.start()
                    
                    _currentSong.value = song
                    _isPlaying.value = true
                    _totalDuration.value = player.duration.toLong()
                    
                    startProgressUpdate()
                    updateAllWidgetsCombined(context)
                    
                    player.setOnCompletionListener {
                        playNext(context)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerVM", "Mahnı ifa edilərkən xəta: ", e)
                _scanMessage.value = "Mahnı oxunarkən xəta baş verdi: ${e.localizedMessage}"
                _isPlaying.value = false
            }
        }
    }

    fun togglePlayback(context: Context) {
        val current = _currentSong.value
        if (current == null) {
            // Play first song in list if available
            val list = _songs.value
            if (list.isNotEmpty()) {
                playSong(context, list.first())
            }
            return
        }

        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                stopProgressUpdate()
            } else {
                try {
                    player.start()
                    _isPlaying.value = true
                    startProgressUpdate()
                } catch (e: Exception) {
                    // If state got corrupted, re-play
                    playSong(context, current)
                }
            }
            updateAllWidgetsCombined(context)
        }
    }

    fun playNext(context: Context) {
        val list = _songs.value
        if (list.isEmpty()) return
        
        val current = _currentSong.value
        val currentIndex = list.indexOfFirst { it.id == current?.id }
        
        val nextIndex = if (currentIndex == -1 || currentIndex == list.size - 1) {
            0
        } else {
            currentIndex + 1
        }
        
        playSong(context, list[nextIndex])
    }

    fun playPrevious(context: Context) {
        val list = _songs.value
        if (list.isEmpty()) return
        
        val current = _currentSong.value
        val currentIndex = list.indexOfFirst { it.id == current?.id }
        
        val prevIndex = if (currentIndex <= 0) {
            list.size - 1
        } else {
            currentIndex - 1
        }
        
        playSong(context, list[prevIndex])
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    player.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
                } else {
                    player.seekTo(positionMs.toInt())
                }
                _playbackPosition.value = positionMs
            } catch (e: Exception) {
                Log.e("MusicPlayerVM", "Seek xətası: ", e)
            }
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(200)
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playbackPosition.value = player.currentPosition.toLong()
                    }
                }
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    // High quality synthetic retro music generation!
    // Creates 3 standard beautiful tracks in external media storage
    fun generateDemoTracks(context: Context) {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanMessage.value = "Nümunə mahnılar hazırlanır..."

        viewModelScope.launch(Dispatchers.IO) {
            val demos = listOf(
                DemoTrackPreset("Nostalji Melodiya", "Kosmik Sintez", doubleArrayOf(261.63, 329.63, 392.00, 523.25, 392.00, 329.63), 11025, 8),
                DemoTrackPreset("Egey Rapsodiyası", "Retro Studio", doubleArrayOf(293.66, 349.23, 440.00, 587.33, 440.00, 349.23), 11025, 6),
                DemoTrackPreset("Bakı Gecələri", "Synth Wave", doubleArrayOf(196.00, 246.94, 293.66, 392.00, 293.66, 246.94), 11025, 9)
            )

            for (preset in demos) {
                val wavBytes = generateSyntheticWavBytes(preset)
                try {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, "${preset.title.replace(" ", "_")}.wav")
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                        put(MediaStore.Audio.Media.ARTIST, preset.artist)
                        put(MediaStore.Audio.Media.ALBUM, "Retro Səslər")
                        put(MediaStore.Audio.Media.TITLE, preset.title)
                        put(MediaStore.Audio.Media.IS_MUSIC, 1)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MusiqiPleyeri")
                        }
                    }

                    val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { targetUri ->
                        context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                            outputStream.write(wavBytes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlayerVM", "Nümunə mahnı '${preset.title}' yazılarkən xəta: ", e)
                }
            }

            // Let content resolver index settle briefly, then re-scan!
            delay(800)
            withContext(Dispatchers.Main) {
                scanSongs(context)
            }
        }
    }

    private fun generateSyntheticWavBytes(preset: DemoTrackPreset): ByteArray {
        val sampleRate = preset.sampleRate
        val durationSeconds = preset.durationSeconds
        val numSamples = sampleRate * durationSeconds
        val dataSize = numSamples * 2
        val totalSize = 44 + dataSize

        val header = ByteArray(totalSize)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        val fileLength = totalSize - 8
        header[4] = (fileLength and 0xff).toByte()
        header[5] = ((fileLength shr 8) and 0xff).toByte()
        header[6] = ((fileLength shr 16) and 0xff).toByte()
        header[7] = ((fileLength shr 24) and 0xff).toByte()

        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // "fmt " chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1
        header[21] = 0

        header[22] = 1
        header[23] = 0

        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        val byteRate = sampleRate * 2
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = 2
        header[33] = 0

        header[34] = 16
        header[35] = 0

        // "data" chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()

        val noteDuration = sampleRate / 2 // 0.5s per note

        for (i in 0 until numSamples) {
            val noteIdx = (i / noteDuration) % preset.frequencies.size
            val freq = preset.frequencies[noteIdx]

            // Angle matching note freq
            val angle = 2.0 * Math.PI * freq * i / sampleRate
            val sineVal = Math.sin(angle)
            
            // Retro triangle or pulse modulation to make it sound incredibly nostalgic
            val waveVal = if (noteIdx % 3 == 0) {
                // Nice square wave chime
                if (sineVal > 0) 0.3 else -0.3
            } else if (noteIdx % 3 == 1) {
                // Triangle style
                val tri = Math.asin(sineVal) * (2.0 / Math.PI)
                tri * 0.5
            } else {
                // Beautiful warm sine wave
                sineVal * 0.6
            }

            // Decay amplitude slightly over the duration of each note for organic feel
            val noteProgress = (i % noteDuration).toDouble() / noteDuration
            val decay = 1.0 - noteProgress * 0.7
            
            val sampleVal = waveVal * decay * 32767.0
            val shortVal = sampleVal.toInt().coerceIn(-32768, 32767)
            val bytePos = 44 + i * 2
            header[bytePos] = (shortVal and 0xff).toByte()
            header[bytePos + 1] = ((shortVal shr 8) and 0xff).toByte()
        }

        return header
    }

    override fun onCleared() {
        super.onCleared()
        if (instance == this) {
            instance = null
        }
        stopProgressUpdate()
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("MusicPlayerVM", "MediaPlayer release xətası: ", e)
        }
        mediaPlayer = null
    }
}

private data class DemoTrackPreset(
    val title: String,
    val artist: String,
    val frequencies: DoubleArray,
    val sampleRate: Int,
    val durationSeconds: Int
)
