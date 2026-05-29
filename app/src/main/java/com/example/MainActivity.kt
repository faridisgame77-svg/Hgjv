package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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

    // Clean gradient background for continuous visual flavor
    val mainGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            Color(0xFF131524),
            Color(0xFF0F101B)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { /* Hamburger Action */ }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Kitabxana",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    if (permissionGranted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (isScanning) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF49454F))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val pulseAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                                    )
                                    Text(
                                        text = "Skan edilir...",
                                        color = Color(0xFFE6E1E9),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.scanSongs(context) },
                                modifier = Modifier.testTag("scan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Mahnı Siyahısını Yenilə",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1C1B1F),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Floating Mini Player (stacked)
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                    exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow))
                ) {
                    currentSong?.let { song ->
                        PlayerMiniPanel(
                            song = song,
                            isPlaying = isPlaying,
                            playbackPosition = playbackPosition,
                            totalDuration = totalDuration,
                            onPlayPause = { viewModel.togglePlayback(context) },
                            onNext = { viewModel.playNext(context) },
                            onPrevious = { viewModel.playPrevious(context) },
                            onSeek = { pos -> viewModel.seekTo(pos) }
                        )
                    }
                }
                
                // Sophisticated Bottom Navigation
                SophisticatedBottomNavigation()
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
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Fayllar skan olunur...",
                                    color = MaterialTheme.colorScheme.onBackground
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Category Label + Elegant Auto-Play Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bütün Mahnılar (${songs.size})".uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 16.sp,
                                letterSpacing = 1.2.sp
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val current = viewModel.isAutoPlayEnabled.value
                                        viewModel.setAutoPlayEnabled(context, !current)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Açılışda avtomatik oynat",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFCAC4D0)
                                )
                                val isAutoPlayEnabled by viewModel.isAutoPlayEnabled.collectAsState()
                                Switch(
                                    checked = isAutoPlayEnabled,
                                    onCheckedChange = { viewModel.setAutoPlayEnabled(context, it) },
                                    thumbContent = if (isAutoPlayEnabled) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        }
                                    } else null,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF381E72),
                                        checkedTrackColor = Color(0xFFD0BCFE),
                                        uncheckedThumbColor = Color(0xFF938F99),
                                        uncheckedTrackColor = Color(0xFF313033)
                                    ),
                                    modifier = Modifier
                                        .scale(0.8f)
                                        .testTag("auto_play_switch")
                                )
                            }
                        }

                        // Scan message chip
                        scanMessage?.let { msg ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Məlumat",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = msg,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("song_list")
                        ) {
                            itemsIndexed(songs) { index, song ->
                                SongItemRow(
                                    song = song,
                                    index = index,
                                    isActive = song.id == currentSong?.id,
                                    isPlaying = isPlaying && song.id == currentSong?.id,
                                    onPlay = { viewModel.playSong(context, song) }
                                )
                            }
                        }
                    }
                    
                    if (isScanning) {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerMiniPanel(
    song: Song,
    isPlaying: Boolean,
    playbackPosition: Long,
    totalDuration: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Rotating vinyl art effect when active and playing!
    val rotationAngle by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Surface(
        color = Color(0xFFEADDFF),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .testTag("player_control_panel")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Track Info Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Vinyl record shape
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .rotate(rotationAngle)
                        .shadow(2.dp, CircleShape)
                        .background(Color(0xFF21005D))
                        .clip(CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.Black)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFFD0BCFE))
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
                        color = Color(0xFF21005D),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee() // Auto scrolling title if too long!
                    )
                    Text(
                        text = song.artist,
                        fontSize = 11.sp,
                        color = Color(0xFF21005D).copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action buttons aligned right in a single line
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
                            tint = Color(0xFF21005D),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play/Pause circular accent
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF21005D))
                            .clickable { onPlayPause() }
                            .testTag("play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Oynat/Durdur",
                            tint = Color.White,
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
                            tint = Color(0xFF21005D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    color = Color(0xFF21005D).copy(alpha = 0.7f),
                    modifier = Modifier.width(32.dp)
                )

                Slider(
                    value = sliderPos,
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .testTag("player_progress_slider"),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF21005D),
                        inactiveTrackColor = Color(0xFF21005D).copy(alpha = 0.20f),
                        thumbColor = Color(0xFF21005D)
                    )
                )

                Text(
                    text = formatTime(totalDuration),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF21005D).copy(alpha = 0.7f),
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun SophisticatedBottomNavigation() {
    Surface(
        color = Color(0xFF2B2930),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFF49454F)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Musiqi (Active)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { /* Active tab */ }
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE8DEF8))
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Musiqi",
                        tint = Color(0xFF1D192B),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Musiqi",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6E1E9)
                )
            }

            // Tab 2: Qovluqlar (Inactive)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clickable { /* Inactive simulated tab */ }
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Qovluqlar",
                    tint = Color(0xFFCAC4D0).copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Qovluqlar",
                    fontSize = 11.sp,
                    color = Color(0xFFCAC4D0).copy(alpha = 0.6f)
                )
            }

            // Tab 3: Ayarlar (Inactive)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clickable { /* Inactive simulated tab */ }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ayarlar",
                    tint = Color(0xFFCAC4D0).copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Ayarlar",
                    fontSize = 11.sp,
                    color = Color(0xFFCAC4D0).copy(alpha = 0.6f)
                )
            }
        }
    }
}
