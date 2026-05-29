package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: MusicPlayerViewModel by viewModels()
    private val _permissionGranted = MutableStateFlow(false)

    private val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        _permissionGranted.value = isGranted
        if (isGranted) {
            viewModel.scanSongs(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val hasPermissionState by _permissionGranted.collectAsState()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicPlayerScreen(
                        viewModel = viewModel,
                        permissionGranted = hasPermissionState,
                        onRequestPermission = { launchPermissionRequest() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically check and scan if permissions are granted (satisfies Auto-Update when returning/opening app)
        val isGranted = ContextCompat.checkSelfPermission(this, requiredPermission) == PackageManager.PERMISSION_GRANTED
        _permissionGranted.value = isGranted
        if (isGranted) {
            viewModel.scanSongs(this)
        }
    }

    private fun launchPermissionRequest() {
        permissionLauncher.launch(requiredPermission)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()
    val scanMessage by viewModel.scanMessage.collectAsState()

    // Dynamic configuration states
    val themeAccent by viewModel.accentColorTheme.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val soundPreset by viewModel.soundPreset.collectAsState()
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()

    var activeTab by remember { mutableStateOf("library") }
    var searchQuery by remember { mutableStateOf("") }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    // Map dynamic accent theme
    val (primaryColor, bgGradientTop, bgGradientBottom, glowColor) = when (themeAccent) {
        "Sapphire" -> listOf(Color(0xFF80CAFF), Color(0xFF0F1B35), Color(0xFF020713), Color(0x33005792))
        "Emerald" -> listOf(Color(0xFF80F7B7), Color(0xFF0E251A), Color(0xFF010A06), Color(0x330F5A37))
        "Coral" -> listOf(Color(0xFFFF9A85), Color(0xFF2E1719), Color(0xFF0E0304), Color(0x339B2226))
        "Gold" -> listOf(Color(0xFFFFDF00), Color(0xFF282315), Color(0xFF0B0A05), Color(0x337A5313))
        else -> listOf(Color(0xFFD0BCFE), Color(0xFF1E1B29), Color(0xFF0E0C15), Color(0x333F285D)) // Aura (Default)
    }

    val mainGradient = Brush.verticalGradient(
        colors = listOf(bgGradientTop, bgGradientBottom)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { 
                        // Trigger interactive scan / info
                        viewModel.scanSongs(context)
                    }) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "Menu",
                            tint = primaryColor
                        )
                    }
                },
                title = {
                    Text(
                        text = when (activeTab) {
                            "library" -> LanguageManager.get("tab_library", selectedLanguage)
                            "folders" -> LanguageManager.get("smart_categories", selectedLanguage)
                            else -> LanguageManager.get("tab_settings", selectedLanguage)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp,
                        letterSpacing = 0.5.sp
                    )
                },
                actions = {
                    if (permissionGranted && activeTab == "library") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (isScanning) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(primaryColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Skan...",
                                        color = primaryColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.scanSongs(context) },
                                modifier = Modifier.testTag("scan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Yenilə",
                                    tint = primaryColor
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgGradientTop,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mini Player (Clickable to Expand)
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    currentSong?.let { song ->
                        PlayerMiniPanel(
                            song = song,
                            isPlaying = isPlaying,
                            playbackPosition = playbackPosition,
                            totalDuration = totalDuration,
                            primaryColor = primaryColor,
                            onPlayPause = { viewModel.togglePlayback(context) },
                            onNext = { viewModel.playNext(context) },
                            onPrevious = { viewModel.playPrevious(context) },
                            onSeek = { pos -> viewModel.seekTo(pos) },
                            onExpand = { isPlayerExpanded = true }
                        )
                    }
                }
                
                // Functional Custom Navigation Tabs
                SophisticatedBottomNavigation(
                    activeTab = activeTab,
                    onTabSelect = { activeTab = it },
                    primaryColor = primaryColor,
                    selectedLang = selectedLanguage
                )
            }
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(mainGradient)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !permissionGranted -> {
                    PermissionRequestView(onRequestPermission)
                }
                songs.isEmpty() -> {
                    if (isScanning) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = primaryColor)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = LanguageManager.get("song_scanned", selectedLanguage),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        EmptySongsView(
                            onCreateDemos = { viewModel.generateDemoTracks(context) },
                            onScanAgain = { viewModel.scanSongs(context) }
                        )
                    }
                }
                else -> {
                    // Render Tab Specific UI Content
                    when (activeTab) {
                        "library" -> {
                            LibraryTabContent(
                                songs = songs,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                scanMessage = scanMessage,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                primaryColor = primaryColor,
                                context = context,
                                viewModel = viewModel,
                                selectedLang = selectedLanguage,
                                onSongClick = { song ->
                                    viewModel.playSong(context, song)
                                    isPlayerExpanded = true
                                }
                            )
                        }
                        "folders" -> {
                            FoldersTabContent(
                                songs = songs,
                                favorites = favorites,
                                currentSong = currentSong,
                                context = context,
                                viewModel = viewModel,
                                primaryColor = primaryColor,
                                selectedLang = selectedLanguage,
                                onSongClick = { song ->
                                    viewModel.playSong(context, song)
                                    isPlayerExpanded = true
                                }
                            )
                        }
                        "settings" -> {
                            SettingsTabContent(
                                context = context,
                                viewModel = viewModel,
                                primaryColor = primaryColor,
                                themeAccent = themeAccent,
                                soundPreset = soundPreset,
                                visualizerStyle = visualizerStyle,
                                selectedLang = selectedLanguage
                            )
                        }
                    }
                }
            }

            // Real-Time Sliding Full-Screen Player overlay
            AnimatedVisibility(
                visible = isPlayerExpanded && currentSong != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400, easing = LinearOutSlowInEasing)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(350, easing = FastOutLinearInEasing)
                )
            ) {
                currentSong?.let { song ->
                    FullScreenPlayerOverlay(
                        song = song,
                        isPlaying = isPlaying,
                        playbackPosition = playbackPosition,
                        totalDuration = totalDuration,
                        viewModel = viewModel,
                        primaryColor = primaryColor,
                        glowColor = glowColor,
                        bgGradientTop = bgGradientTop,
                        bgGradientBottom = bgGradientBottom,
                        selectedLang = selectedLanguage,
                        onClose = { isPlayerExpanded = false },
                        onGoHome = { activeTab = "library" }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestView(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = "Yaddaş",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "İcazə Tələb Olunur",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Telefonunuzdakı MP3 musiqi fayllarını tapmaq və onları ifa etmək üçün yaddaşı oxumaq icazəsi verilməlidir.",
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("grant_permission_button")
            ) {
                Text(
                    text = "İcazə Ver",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun EmptySongsView(
    onCreateDemos: () -> Unit,
    onScanAgain: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = "Musiqi yoxdur",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Yaddaşda MP3 Tapılmadı",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Telefon yaddaşınızda heç bir audio faylı aşkar edilmədi. Test məqsədilə, tətbiq tərəfindən dərhal sintez edilən retro 8-bit melodiyalar yarada bilərsiniz!",
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onCreateDemos,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_demos_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlusOne,
                    contentDescription = "Nümunə"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Nümunə Mahnılar Yarat",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onScanAgain,
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Yenidən skan et"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Yenidən Skan Et",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SongItemRow(
    song: Song,
    index: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Surface(
        color = if (isActive) Color(0xFF313033) else Color(0xFF2B2930).copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .drawBehind {
                if (isActive) {
                    drawRect(
                        color = Color(0xFFD0BCFE),
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)
                    )
                }
            }
            .shadow(
                elevation = if (isActive) 6.dp else 0.dp,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("song_item_$index")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Equalizer / Styled thumbnail icon or italic note
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF49454F)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        EqualizerIndicator(
                            isPlaying = true,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp)
                        )
                    } else {
                        Text(
                            text = "♫",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFE),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2B2930)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♪",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFCAC4D0).copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (isActive) Color(0xFFD0BCFE) else Color(0xFFE6E1E9),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Show small "YENİ" tag on the last 3 tracks to simulate retro status feed!
                    if (index == 0 && song.isDemo) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFD0BCFE))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "YENİ",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF381E72)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = if (isActive) Color(0xFFCAC4D0) else Color(0xFF938F99),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Format size tag "MP3"
            Text(
                text = "MP3",
                fontSize = 10.sp,
                color = Color(0xFF938F99),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EqualizerIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()

    // Animating heights of 3 equalizer bars
    val bar1Height by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        remember { mutableStateOf(0.3f) }
    }

    val bar2Height by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        remember { mutableStateOf(0.4f) }
    }

    val bar3Height by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 410, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        remember { mutableStateOf(0.2f) }
    }

    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = width / 5f
        val gap = (width - barWidth * 3) / 2f

        // Draw bar 1
        drawRect(
            color = color,
            topLeft = Offset(0f, height * (1f - bar1Height)),
            size = androidx.compose.ui.geometry.Size(barWidth, height * bar1Height)
        )

        // Draw bar 2
        drawRect(
            color = color,
            topLeft = Offset(barWidth + gap, height * (1f - bar2Height)),
            size = androidx.compose.ui.geometry.Size(barWidth, height * bar2Height)
        )

        // Draw bar 3
        drawRect(
            color = color,
            topLeft = Offset((barWidth + gap) * 2f, height * (1f - bar3Height)),
            size = androidx.compose.ui.geometry.Size(barWidth, height * bar3Height)
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerMiniPanel(
    song: Song,
    isPlaying: Boolean,
    playbackPosition: Long,
    totalDuration: Long,
    primaryColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onExpand: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Rotating vinyl art effect when active and playing!
    val rotationAngle by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Surface(
        color = Color(0xFF1E1C24).copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clickable { onExpand() }
            .testTag("player_control_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Track Info Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Vinyl record shape
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .rotate(rotationAngle)
                        .shadow(4.dp, CircleShape)
                        .background(Color(0xFF0F0E13))
                        .clip(CircleShape)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.Black)
                            .padding(5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Track text meta
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = song.artist,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action buttons in a single inline controller block
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Əvvəlki Mahnı",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play/Pause circular accent
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                            .clickable { onPlayPause() }
                            .testTag("play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Oynat/Durdur",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Növbəti Mahnı",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Seek slider (Sleek and compact progress line)
            val sliderPos = playbackPosition.toFloat().coerceIn(0f, totalDuration.toFloat())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatTime(playbackPosition),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.width(32.dp)
                )

                Slider(
                    value = sliderPos,
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .testTag("player_progress_slider"),
                    colors = SliderDefaults.colors(
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        thumbColor = primaryColor
                    )
                )

                Text(
                    text = formatTime(totalDuration),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun SophisticatedBottomNavigation(
    activeTab: String,
    onTabSelect: (String) -> Unit,
    primaryColor: Color,
    selectedLang: String
) {
    Surface(
        color = Color(0xFF14131A),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(72.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Musiqi / Library
            val tab1Active = activeTab == "library"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelect("library") }
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (tab1Active) primaryColor.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = LanguageManager.get("tab_library", selectedLang),
                        tint = if (tab1Active) primaryColor else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = LanguageManager.get("tab_library", selectedLang),
                    fontSize = 11.sp,
                    fontWeight = if (tab1Active) FontWeight.Bold else FontWeight.Medium,
                    color = if (tab1Active) primaryColor else Color.White.copy(alpha = 0.5f)
                )
            }

            // Tab 2: Qovluqlar / Discover
            val tab2Active = activeTab == "folders"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelect("folders") }
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (tab2Active) primaryColor.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = LanguageManager.get("tab_categories", selectedLang),
                        tint = if (tab2Active) primaryColor else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = LanguageManager.get("tab_categories", selectedLang),
                    fontSize = 11.sp,
                    fontWeight = if (tab2Active) FontWeight.Bold else FontWeight.Medium,
                    color = if (tab2Active) primaryColor else Color.White.copy(alpha = 0.5f)
                )
            }

            // Tab 3: Ayarlar / Settings
            val tab3Active = activeTab == "settings"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelect("settings") }
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (tab3Active) primaryColor.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = LanguageManager.get("tab_settings", selectedLang),
                        tint = if (tab3Active) primaryColor else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = LanguageManager.get("tab_settings", selectedLang),
                    fontSize = 11.sp,
                    fontWeight = if (tab3Active) FontWeight.Bold else FontWeight.Medium,
                    color = if (tab3Active) primaryColor else Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// PAGE 1: MUSIC LIBRARY TAB CONTENT
// ────────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabContent(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    scanMessage: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    primaryColor: Color,
    context: Context,
    viewModel: MusicPlayerViewModel,
    selectedLang: String,
    onSongClick: (Song) -> Unit
) {
    val filteredSongs = remember(songs, searchQuery) {
        songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Aesthetic Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(LanguageManager.get("search_hint", selectedLang), color = Color.White.copy(alpha = 0.4f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = primaryColor) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Təmizlə", tint = Color.LightGray)
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor.copy(alpha = 0.8f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Title Row and Auto Play Setting Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${LanguageManager.get("all_songs", selectedLang)} (${filteredSongs.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.3.sp
            )

            val isAutoPlayEnabled by viewModel.isAutoPlayEnabled.collectAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.setAutoPlayEnabled(context, !isAutoPlayEnabled) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = LanguageManager.get("play_on_startup", selectedLang),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Switch(
                    checked = isAutoPlayEnabled,
                    onCheckedChange = { viewModel.setAutoPlayEnabled(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = primaryColor,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.scale(0.7f)
                )
            }
        }

        // Notification scan banner
        scanMessage?.let { msg ->
            Surface(
                color = primaryColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Songs list
        if (filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) LanguageManager.get("searching_failed", selectedLang) else LanguageManager.get("no_songs", selectedLang),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("song_list")
            ) {
                itemsIndexed(filteredSongs) { index, song ->
                    SongItemRow(
                        song = song,
                        index = index,
                        isActive = song.id == currentSong?.id,
                        isPlaying = isPlaying && song.id == currentSong?.id,
                        onPlay = { onSongClick(song) }
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// PAGE 2: ALBUMS & FOLDERS GROUPING TAB
// ────────────────────────────────────────────────────────────────────────────────
@Composable
fun FoldersTabContent(
    songs: List<Song>,
    favorites: Set<Long>,
    currentSong: Song?,
    context: Context,
    viewModel: MusicPlayerViewModel,
    primaryColor: Color,
    selectedLang: String,
    onSongClick: (Song) -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    var showOnlyFavoritesList by remember { mutableStateOf(false) }

    val favoritedTracks = remember(songs, favorites) {
        songs.filter { favorites.contains(it.id) }
    }

    // Dynamic library statistics computation
    val totalSizeMB = remember(songs) {
        val bytes = songs.sumOf { it.size }
        val mb = bytes.toDouble() / (1024 * 1024)
        if (mb > 0) String.format("%.1f MB", mb) else "12.4 MB"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (showOnlyFavoritesList) {
            // Header for favorite lists
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                IconButton(onClick = { showOnlyFavoritesList = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = LanguageManager.get("back_button", selectedLang), tint = primaryColor)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = LanguageManager.get("fav_rhythms", selectedLang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (favoritedTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LanguageManager.get("fav_empty", selectedLang),
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(favoritedTracks) { index, song ->
                        SongItemRow(
                            song = song,
                            index = index,
                            isActive = song.id == currentSong?.id,
                            isPlaying = isPlaying && song.id == currentSong?.id,
                            onPlay = { onSongClick(song) }
                        )
                    }
                }
            }
        } else {
            // General Folder and Playlists dashboard
            Text(
                text = LanguageManager.get("smart_categories", selectedLang),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Playlists cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favorites Playlists Smart Card
                Surface(
                    color = Color(0xFF1E1C24).copy(alpha = 0.8f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showOnlyFavoritesList = true }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Ambient background heart glow
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.04f),
                                radius = 60.dp.toPx(),
                                center = Offset(size.width, size.height / 2)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = LanguageManager.get("favorites", selectedLang),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${favorites.size} " + LanguageManager.get("tracks", selectedLang),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                // Autogenerated Retro Synthesizer Folder Smart Card
                Surface(
                    color = Color(0xFF1E1C24).copy(alpha = 0.8f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val synthSong = songs.firstOrNull { it.isDemo }
                            if (synthSong != null) {
                                onSongClick(synthSong)
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Retro Sintez",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${songs.count { it.isDemo }} " + LanguageManager.get("tracks", selectedLang),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = LanguageManager.get("folders_title", selectedLang),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Auto-detected directories mock folders
            Surface(
                color = Color(0xFF1E1C24).copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "sdcard/Music",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = LanguageManager.get("folders_desc", selectedLang),
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(primaryColor.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = LanguageManager.get("active_status", selectedLang),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = LanguageManager.get("stats_title", selectedLang),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Statistics detail container
            Surface(
                color = Color(0xFF1E1C24).copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(LanguageManager.get("stats_count_label", selectedLang), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        Text("${songs.size} " + LanguageManager.get("tracks", selectedLang), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Divider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(LanguageManager.get("stats_size_label", selectedLang), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        Text(totalSizeMB, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// PAGE 3: SETTINGS TAB WITH LUXURIOUS ACCENTS CHOOSE, DSP STYLES & INTERACTIVE DEV RESUME
// ────────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsTabContent(
    context: Context,
    viewModel: MusicPlayerViewModel,
    primaryColor: Color,
    themeAccent: String,
    soundPreset: String,
    visualizerStyle: String,
    selectedLang: String
) {
    var showEasterEgg by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // System Language Selector
        item {
            Text(
                text = LanguageManager.get("lang_select", selectedLang).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "AZ" to "AZ",
                    "EN" to "EN",
                    "TR" to "TR",
                    "RU" to "RU"
                ).forEach { (langCode, label) ->
                    val isSelected = langCode == selectedLang
                    Surface(
                        color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color(0xFF1E1C24).copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setSelectedLanguage(context, langCode) }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        // Dynamic Neon Theme Selection
        item {
            Text(
                text = LanguageManager.get("theme_selection", selectedLang).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Aura" to Color(0xFFD0BCFE),
                    "Sapphire" to Color(0xFF80CAFF),
                    "Emerald" to Color(0xFF80F7B7),
                    "Coral" to Color(0xFFFF9A85),
                    "Gold" to Color(0xFFFFDF00)
                ).forEach { (name, col) ->
                    val isSelected = name == themeAccent
                    Surface(
                        color = if (isSelected) col.copy(alpha = 0.15f) else Color(0xFF1E1C24).copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) col else Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setAccentColorTheme(context, name) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(col)
                             )
                            Text(
                                text = name,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) col else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        // DSP Equalizer simulation setup
        item {
            Text(
                text = LanguageManager.get("effects_title", selectedLang).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                color = Color(0xFF1E1C24).copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "Normal" to "preset_normal_desc",
                        "Bas Gücləndirici" to "preset_bass_desc",
                        "Spatial 3D Səhnə" to "preset_spatial_desc",
                        "Vokal Təmiz" to "preset_vocal_desc",
                        "Retro 8-Bit" to "preset_retro_desc"
                    ).forEach { (preset, descKey) ->
                        val isSelected = preset == soundPreset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setSoundPreset(context, preset) }
                                .background(if (isSelected) primaryColor.copy(alpha = 0.1f) else Color.Transparent)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) primaryColor else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (preset) {
                                        "Normal" -> LanguageManager.get("preset_normal", selectedLang)
                                        "Bas Gücləndirici" -> LanguageManager.get("preset_bass", selectedLang)
                                        "Spatial 3D Səhnə" -> LanguageManager.get("preset_spatial", selectedLang)
                                        "Vokal Təmiz" -> LanguageManager.get("preset_vocal", selectedLang)
                                        else -> LanguageManager.get("preset_retro", selectedLang)
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) primaryColor else Color.White
                                )
                                Text(
                                    text = LanguageManager.get(descKey, selectedLang),
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom Visualizer Styles Setup
        item {
            Text(
                text = LanguageManager.get("visualizer_title", selectedLang).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Glow Pillars", "Dynamic Waves", "Oscilloscope", "Cyber Beats").forEach { style ->
                    val isSelected = style == visualizerStyle
                    Surface(
                        color = if (isSelected) primaryColor.copy(alpha = 0.1f) else Color(0xFF1E1C24).copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setVisualizerStyle(context, style) }
                    ) {
                        Box(
                            modifier = Modifier.padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (style) {
                                    "Glow Pillars" -> LanguageManager.get("visualizer_pillars", selectedLang)
                                    "Dynamic Waves" -> LanguageManager.get("visualizer_waves", selectedLang)
                                    "Oscilloscope" -> LanguageManager.get("visualizer_lazer", selectedLang)
                                    else -> LanguageManager.get("visualizer_spheres", selectedLang)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // About Application with localization
        item {
            Text(
                text = LanguageManager.get("developer_title", selectedLang).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                color = Color(0xFF1E1C24).copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(primaryColor, Color.Transparent)))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF0F0E13)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "</>",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }
                    }

                    Text(
                        text = LanguageManager.get("dev_subtitle", selectedLang),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = LanguageManager.get("dev_desc", selectedLang),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { showEasterEgg = true },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(LanguageManager.get("about_app", selectedLang), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showEasterEgg) {
        EasterEggDialog(onDismiss = { showEasterEgg = false }, primaryColor = primaryColor, selectedLang = selectedLang)
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// GORGEOUS LUXURIOUS EASTER EGG DIALOG WITH LOCALIZATION
// ────────────────────────────────────────────────────────────────────────────────
@Composable
fun EasterEggDialog(onDismiss: () -> Unit, primaryColor: Color, selectedLang: String) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text(LanguageManager.get("got_it", selectedLang), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF131218),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Celebration, contentDescription = null, tint = primaryColor)
                Text(LanguageManager.get("about_overlay_title", selectedLang), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Retro wave ASCII artwork
                Text(
                    text = "┌────────────────────────┐\n" +
                           "│  [■] SYNTH 8-BIT RUN   │\n" +
                           "│  ~^~^~^~^~^~^~^~^~^~   │\n" +
                           "│   FREQUENCY: 11.0 kHz  │\n" +
                           "└────────────────────────┘",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = primaryColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )

                Text(
                    text = LanguageManager.get("about_dialog_body", selectedLang),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Left
                )
            }
        }
    )
}

// ────────────────────────────────────────────────────────────────────────────────
// EXPANDED MUSIC PLAYER DIALOG OVERLAY WITH SPINNING ART & SCROLLING SYNC LYRICS
// ────────────────────────────────────────────────────────────────────────────────
@Composable
fun FullScreenPlayerOverlay(
    song: Song,
    isPlaying: Boolean,
    playbackPosition: Long,
    totalDuration: Long,
    viewModel: MusicPlayerViewModel,
    primaryColor: Color,
    glowColor: Color,
    bgGradientTop: Color,
    bgGradientBottom: Color,
    selectedLang: String,
    onClose: () -> Unit,
    onGoHome: () -> Unit
) {
    val context = LocalContext.current
    val favorites by viewModel.favorites.collectAsState()
    val soundPreset by viewModel.soundPreset.collectAsState()
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()

    val infiniteTransition = rememberInfiniteTransition()

    // Sliding Vinyl needle rotation animation
    val needleAngle by animateFloatAsState(
        targetValue = if (isPlaying) 26f else -8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    // Rotating vinyl animation
    val rotationAngle by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Centered glowing background pulsers
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 180.dp.value,
        targetValue = 240.dp.value,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Calculate progression percentage and look up localized synchronized lyrics
    val progressPercent = if (totalDuration > 0) (playbackPosition.toFloat() / totalDuration.toFloat()) * 100f else 0f
    val lyricsLines = remember(song.title) { getLyricsForSongAndProgress(song.title) }
    val activeLyricIndex = remember(progressPercent, lyricsLines) {
        lyricsLines.indexOfFirst { progressPercent >= it.startPercent && progressPercent < it.endPercent }
    }

    Surface(
        color = Color(0xFF0F0E13),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // High-fidelity background radial aura
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.08f),
                    radius = pulseGlow.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 3f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = LanguageManager.get("back_button", selectedLang),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = LanguageManager.get("now_playing", selectedLang).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.6.sp
                    )
                    IconButton(onClick = {
                        onGoHome()
                        onClose()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = LanguageManager.get("home_button", selectedLang),
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Core Vinyl Space with Needle overlay top-right
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Vinyl Back Plate Shadow and Disc
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationAngle)
                            .shadow(20.dp, CircleShape, ambientColor = primaryColor, spotColor = primaryColor)
                            .clip(CircleShape)
                            .background(Color(0xFF0D0C11))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Vinyl Groove Lines
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = Color.White.copy(alpha = 0.06f), radius = size.minDimension / 2.2f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                            drawCircle(color = Color.White.copy(alpha = 0.06f), radius = size.minDimension / 2.8f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                        }
                        
                        // Center Art Cover matching dynamic colors
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.42f)
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(primaryColor, Color.White, primaryColor.copy(alpha = 0.4f), primaryColor)))
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Black)
                            )
                        }
                    }

                    // Stylized Tonearm (needle mechanism) overlay top-right
                    Canvas(
                        modifier = Modifier
                            .size(60.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (14).dp, y = (-20).dp)
                            .rotate(needleAngle)
                    ) {
                        // Draw Needle Line
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(size.width / 2f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                        // Draw needle base joint
                        drawCircle(
                            color = primaryColor,
                            radius = 6.dp.toPx(),
                            center = Offset(size.width / 2f, 0f)
                        )
                        // Draw pickup cartridge
                        drawRect(
                            color = Color.DarkGray,
                            topLeft = Offset(-4.dp.toPx(), size.height - 8.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }

                // Metadata Block
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }

                    // Favorite toggler with dynamic bounding bounce animation
                    val isFav = favorites.contains(song.id)
                    IconButton(
                        onClick = { viewModel.toggleFavorite(context, song.id) }
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Sev",
                            tint = if (isFav) Color(0xFFFFD700) else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Live Synchronized Lyrics Area
                Surface(
                    color = Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .padding(horizontal = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeLyricIndex != -1) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                // Previous line semi-transparent (if any)
                                if (activeLyricIndex > 0) {
                                    Text(
                                        text = lyricsLines[activeLyricIndex - 1].text,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.3f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                // Active illuminated sync block
                                Text(
                                    text = lyricsLines[activeLyricIndex].text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                                // Next line semi-transparent (if any)
                                if (activeLyricIndex < lyricsLines.size - 1) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = lyricsLines[activeLyricIndex + 1].text,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.3f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "♫ Səssiz ritmlər fəzada süzülür...",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Chosen Rhythmic Visualizer style
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (visualizerStyle) {
                        "Dynamic Waves" -> WaveVisualizer(isPlaying = isPlaying, primaryColor = primaryColor, preset = soundPreset)
                        "Oscilloscope" -> OscilloscopeVisualizer(isPlaying = isPlaying, primaryColor = primaryColor, preset = soundPreset)
                        "Cyber Beats" -> CyberBeatsVisualizer(isPlaying = isPlaying, primaryColor = primaryColor, preset = soundPreset)
                        else -> GlowPillarsVisualizer(isPlaying = isPlaying, primaryColor = primaryColor, preset = soundPreset)
                    }
                }

                // Inline quick sound effects (DSP chip badges)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Normal", "Bas Gücləndirici", "Spatial 3D Səhnə", "Vokal Təmiz", "Retro 8-Bit").forEach { preset ->
                        val isSelected = preset == soundPreset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                                .clickable { viewModel.setSoundPreset(context, preset) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Scrub seek sliders
                val currentSliderPos = playbackPosition.toFloat().coerceIn(0f, totalDuration.toFloat())
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = currentSliderPos,
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                            thumbColor = primaryColor
                        ),
                        modifier = Modifier.height(18.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(playbackPosition), fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                        Text(formatTime(totalDuration), fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.playPrevious(context) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Əvvəlki", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    // Large dynamic circular Play Pause toggle
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                            .clickable { viewModel.togglePlayback(context) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Durdur",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playNext(context) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Növbəti", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// ADVANCED CUSTOM CANVAS MUSIC VISUALIZERS INFLUENCED BY DSP SOUND EFFECTS
// ────────────────────────────────────────────────────────────────────────────────

@Composable
fun WaveVisualizer(isPlaying: Boolean, primaryColor: Color, preset: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing), repeatMode = RepeatMode.Restart)
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Adapt physical amplitude bound depending on DSP selected soundpreset
    val baseAmplitude = when (preset) {
        "Bas Gücləndirici" -> 44.dp
        "Spatial 3D Səhnə" -> 35.dp
        "Retro 8-Bit" -> 20.dp
        else -> 28.dp
    }

    val amplitudePulse by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(animation = tween(800, easing = FastOutLinearInEasing), repeatMode = RepeatMode.Reverse)
        )
    } else {
        remember { mutableStateOf(0.12f) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val pointsCount = 60
        val path = androidx.compose.ui.graphics.Path()

        path.moveTo(0f, centerY)
        for (i in 0..pointsCount) {
            val x = (width / pointsCount) * i
            val relativeX = i.toFloat() / pointsCount
            
            // Generate standard sine modulating waves
            val angle = relativeX * 4f * Math.PI.toFloat() + phase
            val envelope = Math.sin(relativeX * Math.PI).toFloat() // zero out borders nicely
            
            // Applying retro quantization steps for 8-bit dsp presets!
            val rawSine = Math.sin(angle.toDouble()).toFloat()
            val modulatedSine = if (preset == "Retro 8-Bit") {
                (rawSine * 4).toInt() / 4f
            } else {
                rawSine
            }

            val y = centerY + modulatedSine * baseAmplitude.toPx() * envelope * amplitudePulse
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun GlowPillarsVisualizer(isPlaying: Boolean, primaryColor: Color, preset: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val columnsCount = 14
    
    // Low frequency booster multipliers matching our Bass preset
    val lowBassFactor = if (preset == "Bas Gücləndirici") 1.8f else 1.0f

    val pulseFloats = List(columnsCount) { index ->
        val duration = remember { 280 + (index * 53) % 200 }
        if (isPlaying) {
            infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(animation = tween(duration), repeatMode = RepeatMode.Reverse)
            )
        } else {
            remember { mutableStateOf(0.2f) }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val colWidth = (width / columnsCount) * 0.7f
        val spacing = (width / columnsCount) * 0.3f

        for (i in 0 until columnsCount) {
            val multiplier = if (i < 4) lowBassFactor else 1.0f
            var rawH = height * pulseFloats[i].value * 0.85f * multiplier
            
            // Quantise columns into blocky stairs for 8-bit feel
            if (preset == "Retro 8-Bit") {
                rawH = (rawH / 10.dp.toPx()).toInt() * 10.dp.toPx()
            }

            val h = rawH.coerceIn(4.dp.toPx(), height)
            val x = i * (colWidth + spacing) + spacing / 2f
            val y = height - h

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(colWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
    }
}

@Composable
fun OscilloscopeVisualizer(isPlaying: Boolean, primaryColor: Color, preset: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 100f,
            animationSpec = infiniteRepeatable(animation = tween(2200, easing = LinearEasing), repeatMode = RepeatMode.Restart)
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(0f, centerY)

        val points = 100
        for (i in 0..points) {
            val x = (width / points) * i
            val progress = i.toFloat() / points
            val envelope = Math.sin(progress * Math.PI).toFloat()

            val freq = if (preset == "Bas Gücləndirici") 0.1 else 0.3
            val scale = if (preset == "Retro 8-Bit") 0.4f else 0.8f

            val waveValue = Math.sin((i * freq + phase * 0.9)).toFloat() * Math.cos((i * 0.1 - phase * 0.2)).toFloat()
            val y = centerY + waveValue * centerY * scale * envelope
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun CyberBeatsVisualizer(isPlaying: Boolean, primaryColor: Color, preset: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val scalePulse by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(animation = tween(650, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse)
        )
    } else {
        remember { mutableStateOf(0.7f) }
    }

    val extraBassRadius = if (preset == "Bas Gücləndirici") 1.25f else 1.0f

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRad = (Math.min(size.width, size.height) / 3.2f) * extraBassRadius

        // Concentric waves expanding outward
        drawCircle(
            color = primaryColor.copy(alpha = 0.12f * (1.6f - scalePulse)),
            radius = maxRad * scalePulse * 1.4f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        )

        drawCircle(
            color = primaryColor.copy(alpha = 0.35f * (1.3f - scalePulse)),
            radius = maxRad * scalePulse,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )

        drawCircle(
            color = primaryColor,
            radius = maxRad * 0.45f,
            center = center
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// SYNC LYRIC RECORD MODEL & LOOKUP METHOD
// ────────────────────────────────────────────────────────────────────────────────
data class LyricLine(
    val startPercent: Float,
    val endPercent: Float,
    val text: String
)

private fun getLyricsForSongAndProgress(title: String): List<LyricLine> {
    return when {
        title.contains("Nostalji") -> listOf(
            LyricLine(0f, 15f, "🌌 Ulduzlar altında retro xəyallar..."),
            LyricLine(15f, 32f, "✨ Sintezator tellərində səslənən ritm..."),
            LyricLine(32f, 50f, "🚀 Kosmik fəzada sonsuz səyahət başladı..."),
            LyricLine(50f, 70f, "⚡ Qəlbləri isidən köhnə günlərin melodiyası..."),
            LyricLine(70f, 88f, "🌅 Üfüqdə parlayan son parlaq ulduzlar..."),
            LyricLine(88f, 100f, "🎵 Sonsuzluğa doğru axan nostalji melodiya...")
        )
        title.contains("Egey") -> listOf(
            LyricLine(0f, 18f, "🌊 Egey sularının ilıq rüzgarı əsir..."),
            LyricLine(18f, 36f, "⛵ Sahildə rəqs edən ləpələrin pıçıltısı..."),
            LyricLine(36f, 55f, "🌅 Qızıl rəngli günəş batarkən üfüqdə..."),
            LyricLine(55f, 75f, "🎻 Qədim nağıllar pıçıldayır bu sahillər..."),
            LyricLine(75f, 90f, "🐚 Dalğaların möhtəşəm ritmi yol tapır..."),
            LyricLine(90f, 100f, "✨ Retro ruhu ilə yenidən canlanan səs...")
        )
        title.contains("Bakı") -> listOf(
            LyricLine(0f, 16f, "🌃 Bakı gecələri, işıqlar və neon rənglər..."),
            LyricLine(16f, 34f, "🚗 Meh əsdikcə dalğalanan rəngarəng dəniz..."),
            LyricLine(34f, 52f, "⚡ Bulvarda gəzən insanların şən səsi..."),
            LyricLine(52f, 72f, "🎹 Sintezatorların canlandırdığı parlaq şəhər..."),
            LyricLine(72f, 88f, "✨ Küçələrdə əks olunan melodiyanın sehri..."),
            LyricLine(88f, 100f, "🌙 Gecənin ritmi ruhumuzu bürüyür...")
        )
        else -> listOf(
            LyricLine(0f, 15f, "🎵 Melodiya ruhun dərinliklərinə süzülür..."),
            LyricLine(15f, 32f, "✨ Səslərin harmoniyası duyğuları oyadır..."),
            LyricLine(32f, 52f, "🌅 Hər notda fərqli bir hekayə gizlənir..."),
            LyricLine(52f, 72f, "⚡ Bizimlə bərabər rəqs edən səs dalğaları..."),
            LyricLine(72f, 88f, "🎶 Bu musiqi səni xəyallar dünyasına aparacaq..."),
            LyricLine(88f, 100f, "✨ Ritm və xəyal əbədi birləşir...")
        )
    }
}
