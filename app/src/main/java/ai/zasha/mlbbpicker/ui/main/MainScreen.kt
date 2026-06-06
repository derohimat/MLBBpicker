package ai.zasha.mlbbpicker.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import ai.zasha.mlbbpicker.data.Hero
import ai.zasha.mlbbpicker.data.HeroRepository
import ai.zasha.mlbbpicker.theme.MLBBPickerTheme

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel,
    onRequestOverlayPermission: () -> Unit,
    onRequestUsagePermission: () -> Unit,
    onToggleService: () -> Unit,
    onToggleAutoDetect: (Boolean) -> Unit,
    onToggleAutoHide: (Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedHeroDetails by remember { mutableStateOf<Hero?>(null) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Hero Wiki

    val filteredHeroes = remember(searchQuery, state.heroes) {
        if (searchQuery.isBlank()) {
            state.heroes
        } else {
            state.heroes.filter { it.hero_name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF64748B)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Dashboard") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD4AF37),
                        selectedTextColor = Color(0xFFD4AF37),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF334155)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Hero Wiki") },
                    label = { Text("Heroes") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD4AF37),
                        selectedTextColor = Color(0xFFD4AF37),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF334155)
                    )
                )
            }
        },
        containerColor = Color(0xFF0F172A) // Sleek Dark theme
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // App Title Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MLBB PICKER",
                        color = Color(0xFFD4AF37), // Gold
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Smart Draft Overlay Companion",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (activeTab == 0) {
                // Settings & Dashboard
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Service Control Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Floating Assistant Service",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (state.isServiceRunning) Color(0xFF10B981) else Color(0xFF64748B))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (state.isServiceRunning) "Status: Running" else "Status: Stopped",
                                            color = if (state.isServiceRunning) Color(0xFF10B981) else Color(0xFF94A3B8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Button(
                                    onClick = onToggleService,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isServiceRunning) Color(0xFFEF4444) else Color(0xFFD4AF37),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(if (state.isServiceRunning) "Stop" else "Start")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFF334155))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Settings switches
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Detect MLBB Game", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Automatically show bubble when game starts", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                                Switch(
                                    checked = state.autoDetectEnabled,
                                    onCheckedChange = onToggleAutoDetect,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFD4AF37),
                                        checkedTrackColor = Color(0xFF475569)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Hide when Game Minimizes", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Hides bubble if game is in background", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                                Switch(
                                    checked = state.autoHideEnabled,
                                    onCheckedChange = onToggleAutoHide,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFD4AF37),
                                        checkedTrackColor = Color(0xFF475569)
                                    )
                                )
                            }
                        }
                    }

                    // Permissions Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "System Permissions Required",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "These are required for automatic foreground detection and overlay rendering over the game.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Permission 1: Overlay
                            PermissionRow(
                                title = "Draw Over Other Apps",
                                isGranted = state.isDrawOverlayGranted,
                                onActionClick = onRequestOverlayPermission
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFF334155))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Permission 2: Usage Stats
                            PermissionRow(
                                title = "Usage Stats Access",
                                isGranted = state.isUsageStatsGranted,
                                onActionClick = onRequestUsagePermission
                            )
                        }
                    }
                }
            } else {
                // Hero Database / Wiki Tab
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search hero...", color = Color(0xFF94A3B8)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredHeroes) { hero ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedHeroDetails = hero }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = hero.img_src,
                                    contentDescription = hero.hero_name,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color(0xFF334155), CircleShape)
                                        .background(Color(0xFF1E293B)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = hero.hero_name,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Hero Details Popup Dialog
    if (selectedHeroDetails != null) {
        val hero = selectedHeroDetails!!
        val context = LocalContext.current
        val repository = remember { HeroRepository(context) }
        var countersList by remember { mutableStateOf<List<ai.zasha.mlbbpicker.data.CounterSuggestion>>(emptyList()) }

        LaunchedEffect(hero) {
            countersList = repository.getCounterSuggestions(listOf(hero.id)).take(5)
        }

        AlertDialog(
            onDismissRequest = { selectedHeroDetails = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = hero.img_src,
                        contentDescription = hero.hero_name,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFFD4AF37), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = hero.hero_name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(text = hero.role, color = Color(0xFFD4AF37), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Main Lane", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            Text(hero.lane, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Speciality", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            Text(hero.speciality, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    Text("Top Counters", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    if (countersList.isEmpty()) {
                        Text("Loading counters...", color = Color(0xFF64748B), fontSize = 11.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            countersList.forEach { counter ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = counter.imgSrc,
                                        contentDescription = counter.heroName,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = counter.heroName,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0x33EF4444), RoundedCornerShape(4.dp))
                                            .border(0.5.dp, Color(0xFFEF4444), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "Score: ${String.format("%.1f", counter.score)}",
                                            color = Color(0xFFEF4444),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedHeroDetails = null }) {
                    Text("Close", color = Color(0xFFD4AF37))
                }
            },
            containerColor = Color(0xFF0F172A),
            textContentColor = Color.White
        )
    }
}

@Composable
fun PermissionRow(
    title: String,
    isGranted: Boolean,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isGranted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Granted", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Missing Permission", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!isGranted) {
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33D4AF37), contentColor = Color(0xFFD4AF37)),
                border = BorderStroke(1.dp, Color(0xFFD4AF37)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Simple implementation for collectAsStateWithLifecycle backport to avoid dependency discrepancies
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> {
    return collectAsState()
}
