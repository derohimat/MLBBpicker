package ai.zasha.mlbbpicker.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.*
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch

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
import ai.zasha.mlbbpicker.data.BuildRepository
import ai.zasha.mlbbpicker.data.HeroBuild
import ai.zasha.mlbbpicker.data.HeroMetaStats
import ai.zasha.mlbbpicker.data.DraftManager
import ai.zasha.mlbbpicker.data.SoloQueueManager
import ai.zasha.mlbbpicker.data.SoloHeroRank
import ai.zasha.mlbbpicker.data.PremiumManager
import ai.zasha.mlbbpicker.data.PremiumFeature
import ai.zasha.mlbbpicker.data.MetaStatsRepository
import ai.zasha.mlbbpicker.service.OverlayPanelContent
import ai.zasha.mlbbpicker.theme.MLBBPickerTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel,
    onRequestOverlayPermission: () -> Unit,
    onRequestUsagePermission: () -> Unit,
    onToggleService: () -> Unit,
    onToggleAutoDetect: (Boolean) -> Unit,
    onToggleAutoHide: (Boolean) -> Unit,
    billingManager: ai.zasha.mlbbpicker.data.BillingManager,
    activity: android.app.Activity
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedHeroDetails by remember { mutableStateOf<Hero?>(null) }
    var activeTab by remember { mutableStateOf(0) } // 0=Settings, 1=Draft, 2=Solo, 3=Heroes, 4=Meta
    val context = LocalContext.current
    val heroRepository = remember { HeroRepository(context) }
    val buildRepository = remember { BuildRepository(context) }
    val metaStatsRepository = remember { MetaStatsRepository(context) }
    val isPremium by PremiumManager.isPremium.collectAsState()

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
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 10.sp) },
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
                    icon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Draft"
                        )
                    },
                    label = { Text("Draft", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD4AF37),
                        selectedTextColor = Color(0xFFD4AF37),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF334155)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Solo Queue"
                        )
                    },
                    label = { Text("Solo", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD4AF37),
                        selectedTextColor = Color(0xFFD4AF37),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF334155)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Hero Wiki") },
                    label = { Text("Heroes", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD4AF37),
                        selectedTextColor = Color(0xFFD4AF37),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF334155)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 4,
                    onClick = { activeTab = 4 },
                    icon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Meta Stats"
                        )
                    },
                    label = { Text("Meta", fontSize = 10.sp) },
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
                    .background(Color(0xFF0F172A)) // Seamless blend with Scaffold background
                    .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Gold theme crown/star accent
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFD4AF37),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MLBB PICKER",
                                color = Color(0xFFD4AF37),
                                fontWeight = FontWeight.Black,
                                fontSize = 21.sp,
                                letterSpacing = 2.sp
                            )
                        }
                        // Premium Tier Badge
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isPremium) {
                                        Brush.horizontalGradient(listOf(Color(0xFFD4AF37), Color(0xFFB45309)))
                                    } else {
                                        Brush.horizontalGradient(listOf(Color(0xFF475569), Color(0xFF334155)))
                                    },
                                    RoundedCornerShape(6.dp)
                                        )
                                .border(
                                    0.5.dp,
                                    if (isPremium) Color(0xFFD4AF37) else Color(0xFF64748B),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isPremium) "PRO ACTIVE" else "FREE VERSION",
                                color = if (isPremium) Color.Black else Color(0xFFE2E8F0),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Real-Time Counter & Synergy Assistant",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 28.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Sleek thin golden bottom divider line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFD4AF37).copy(alpha = 0.0f),
                                        Color(0xFFD4AF37).copy(alpha = 0.25f),
                                        Color(0xFFD4AF37).copy(alpha = 0.0f)
                                    )
                                )
                            )
                    )
                }
            }

            when (activeTab) {
                0 -> {
                    // Settings & Dashboard
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ─── Pro Upgrade Card ─────────────────────────────────
                        val planType by PremiumManager.planType.collectAsState()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(
                                1.5.dp,
                                if (isPremium) Color(0xFFD4AF37) else Color(0xFF334155)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isPremium) {
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xFF1E293B), Color(0xFF1E293B), Color(0xFF3C3010))
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xFF1E293B), Color(0xFF1E293B))
                                            )
                                        }
                                    )
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (isPremium) "MLBB PICKER PRO ACTIVE" else "UPGRADE TO PRO",
                                            color = Color(0xFFD4AF37),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = if (isPremium) "Enjoy your premium draft features!" else "Unlock advanced solo and full-screen tools",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color(0xFFD4AF37).copy(alpha = if (isPremium) 0.2f else 0.1f),
                                                CircleShape
                                            )
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Pro Icon",
                                            tint = Color(0xFFD4AF37),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Color(0xFF334155))
                                Spacer(modifier = Modifier.height(12.dp))

                                // Feature comparison
                                Text("Premium Advantages:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        "⚡ Solo Queue carry rankings & best builds",
                                        "📱 Full-screen in-app Draft assistant",
                                        "🚀 Priority OTA data patches & updates",
                                        "💎 Full access to counters and ban algorithms"
                                    ).forEach { benefit ->
                                        Text(benefit, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                if (isPremium) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFD4AF37).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Active Plan: " + planType.replaceFirstChar { it.uppercase() },
                                            color = Color(0xFFD4AF37),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                } else {
                                    val lifetimePrice = billingManager.getFormattedPrice(ai.zasha.mlbbpicker.data.BillingManager.PRODUCT_LIFETIME) ?: "$4.99"
                                    val monthlyPrice = billingManager.getFormattedPrice(ai.zasha.mlbbpicker.data.BillingManager.PRODUCT_MONTHLY) ?: "$0.99"

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                billingManager.launchPurchaseFlow(activity, ai.zasha.mlbbpicker.data.BillingManager.PRODUCT_LIFETIME)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Lifetime Pro", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(lifetimePrice, color = Color.Black.copy(alpha = 0.7f), fontSize = 10.sp)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                billingManager.launchPurchaseFlow(activity, ai.zasha.mlbbpicker.data.BillingManager.PRODUCT_MONTHLY)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, Color(0xFF475569)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Monthly Pro", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("$monthlyPrice/mo", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Service Control Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF111827))))
                                    .padding(16.dp)
                            ) {
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
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            if (state.isServiceRunning) "Stop" else "Start",
                                            fontWeight = FontWeight.Bold
                                        )
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
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF111827))))
                                    .padding(16.dp)
                            ) {
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // Data Update Settings Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF111827))))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Offline Data Patch",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Download latest hero recommendations, item builds, and meta statistics from GitHub raw storage.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                if (state.isUpdatingPatch) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        LinearProgressIndicator(
                                            progress = { state.patchUpdateProgress },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFFD4AF37),
                                            trackColor = Color(0xFF334155)
                                        )
                                        Text(
                                            text = "Updating: ${state.patchUpdateStatus} (${(state.patchUpdateProgress * 100).toInt()}%)",
                                            color = Color(0xFFD4AF37),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    if (state.patchUpdateStatus.isNotEmpty()) {
                                        Text(
                                            text = state.patchUpdateStatus,
                                            color = if (state.patchUpdateStatus.contains("success", ignoreCase = true) || state.patchUpdateStatus.contains("reloaded", ignoreCase = true)) Color(0xFF10B981) else Color(0xFFEF4444),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }

                                    val context = LocalContext.current
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.triggerPatchUpdate(context) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFD4AF37),
                                                contentColor = Color.Black
                                            ),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Update Data", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = { viewModel.clearPatchUpdate(context) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF374151),
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Clear Patches", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // ─── Debug Tools (Premium Simulator) ──────────────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Developer Testing Tools",
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Simulate Pro Account", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Toggle off to lock Pro features; toggle on to unlock", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = isPremium,
                                        onCheckedChange = { enable ->
                                            if (enable) {
                                                PremiumManager.unlockPremium("lifetime", "mock_debug_token")
                                            } else {
                                                PremiumManager.revokePremium()
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFFD4AF37),
                                            checkedTrackColor = Color(0xFF475569)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Full-Screen Draft Assistant
                    Box(modifier = Modifier.fillMaxSize()) {
                        OverlayPanelContent(
                            heroes = state.heroes,
                            selectedEnemies = DraftManager.selectedEnemies,
                            selectedAllies = DraftManager.selectedAllies,
                            counterSuggestions = DraftManager.counterSuggestions,
                            synergySuggestions = DraftManager.synergySuggestions,
                            metaStats = state.metaStats,
                            banRecommendations = DraftManager.banRecommendations,
                            buildRepository = buildRepository,
                            isFullScreen = true,
                            onCollapse = { /* no-op in full screen */ },
                            onClearAll = {
                                DraftManager.clear()
                            },
                            onUpdateRecommendations = {
                                DraftManager.updateRecommendations(
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
                                    heroRepository,
                                    state.metaStats
                                )
                            },
                            onSwapSlots = { fromType, fromIdx, toType, toIdx ->
                                DraftManager.swapSlots(fromType, fromIdx, toType, toIdx)
                                DraftManager.updateRecommendations(
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
                                    heroRepository,
                                    state.metaStats
                                )
                            },
                            onSelectHero = { type, index, hero ->
                                val list = if (type == "enemy") DraftManager.selectedEnemies else DraftManager.selectedAllies
                                list[index] = hero
                                DraftManager.updateRecommendations(
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
                                    heroRepository,
                                    state.metaStats
                                )
                            },
                            onDismiss = { /* no-op in full screen */ }
                        )

                        // Premium lock overlay
                        if (!isPremium) {
                            PremiumLockOverlay(
                                featureName = "Full-Screen Draft Assistant",
                                onUpgradeClick = { activeTab = 0 }
                            )
                        }
                    }
                }
                2 -> {
                    // Solo Queue Tab
                    Box(modifier = Modifier.fillMaxSize()) {
                        SoloQueueScreen(
                            heroes = state.heroes,
                            metaStats = state.metaStats,
                            buildRepository = buildRepository
                        )

                        // Premium lock overlay
                        if (!isPremium) {
                            PremiumLockOverlay(
                                featureName = "Solo Queue Recommendations",
                                onUpgradeClick = { activeTab = 0 }
                            )
                        }
                    }
                }
                3 -> {
                    // Hero Database / Wiki Tab
                    var heroRoleFilter by remember { mutableStateOf<String?>(null) }
                    var heroSortBy by remember { mutableStateOf("Name") }
                    var sortExpanded by remember { mutableStateOf(false) }

                    val sortedFilteredHeroes = remember(filteredHeroes, heroRoleFilter, heroSortBy, state.metaStats) {
                        var list = if (heroRoleFilter != null) {
                            filteredHeroes.filter { it.roleList.any { r -> r.equals(heroRoleFilter, true) } }
                        } else {
                            filteredHeroes
                        }
                        val statsMap = state.metaStats.associateBy { it.heroId }
                        when (heroSortBy) {
                            "Name" -> list.sortedBy { it.hero_name }
                            "Win Rate" -> list.sortedByDescending { statsMap[it.id]?.winRate ?: 0.0 }
                            "Pick Rate" -> list.sortedByDescending { statsMap[it.id]?.pickRate ?: 0.0 }
                            "Ban Rate" -> list.sortedByDescending { statsMap[it.id]?.banRate ?: 0.0 }
                            else -> list
                        }
                    }

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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Role Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val roles = listOf(null to "All", "Fighter" to "Fighter", "Tank" to "Tank", "Mage" to "Mage", "Marksman" to "MM", "Assassin" to "Assassin", "Support" to "Support")
                            roles.forEach { (role, label) ->
                                val isSelected = heroRoleFilter == role
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) Color(0xFFD4AF37) else Color(0xFF1E293B),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFFD4AF37) else Color(0xFF334155),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { heroRoleFilter = role }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Sort Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${sortedFilteredHeroes.size} heroes",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                            Box {
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                        .clickable { sortExpanded = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sort: $heroSortBy",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = sortExpanded,
                                    onDismissRequest = { sortExpanded = false },
                                    containerColor = Color(0xFF1E293B)
                                ) {
                                    listOf("Name", "Win Rate", "Pick Rate", "Ban Rate").forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    option,
                                                    color = if (heroSortBy == option) Color(0xFFD4AF37) else Color.White,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                heroSortBy = option
                                                sortExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sortedFilteredHeroes) { hero ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedHeroDetails = hero }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val winStats = state.metaStats.find { it.heroId == hero.id }
                                    Box(modifier = Modifier.size(56.dp)) {
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
                                        if (winStats != null) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .background(
                                                        if (winStats.winRate >= 52) Color(0xFF10B981) else if (winStats.winRate >= 50) Color(0xFF3B82F6) else Color(0xFFEF4444),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
                                            ) {
                                                Text(
                                                    text = "${String.format("%.0f", winStats.winRate)}%",
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
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
                                    // Show sort metric below name
                                    if (heroSortBy != "Name" && winStats != null) {
                                        val metricText = when (heroSortBy) {
                                            "Win Rate" -> "${String.format("%.1f", winStats.winRate)}%"
                                            "Pick Rate" -> "${String.format("%.1f", winStats.pickRate)}%"
                                            "Ban Rate" -> "${String.format("%.1f", winStats.banRate)}%"
                                            else -> ""
                                        }
                                        if (metricText.isNotEmpty()) {
                                            Text(
                                                text = metricText,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    MetaStatsTabContent(
                        metaStats = state.metaStats,
                        onHeroClick = { selectedHeroDetails = it },
                        heroes = state.heroes
                    )
                }
            }
        }
    }

    // Hero Details Popup Dialog
    if (selectedHeroDetails != null) {
        val hero = selectedHeroDetails!!
        val context = LocalContext.current
        val repository = remember { HeroRepository(context) }
        val buildRepository = remember { BuildRepository(context) }
        var countersList by remember { mutableStateOf<List<ai.zasha.mlbbpicker.data.CounterSuggestion>>(emptyList()) }
        var buildsList by remember { mutableStateOf<List<HeroBuild>>(emptyList()) }

        LaunchedEffect(hero) {
            countersList = repository.getCounterSuggestions(listOf(hero.id)).take(5)
            buildsList = buildRepository.getBuildsForHero(hero.id)
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
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

                    val winStats = remember(hero, state.metaStats) {
                        state.metaStats.find { it.heroId == hero.id }
                    }

                    if (winStats != null) {
                        HorizontalDivider(color = Color(0xFF334155))
                        Text("Meta Statistics", color = Color(0xFFD4AF37), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Win Rate", color = Color(0xFF94A3B8), fontSize = 9.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${String.format("%.1f", winStats.winRate)}%",
                                    color = if (winStats.winRate >= 50) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Pick Rate", color = Color(0xFF94A3B8), fontSize = 9.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${String.format("%.1f", winStats.pickRate)}%",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Ban Rate", color = Color(0xFF94A3B8), fontSize = 9.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${String.format("%.1f", winStats.banRate)}%",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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

                    HorizontalDivider(color = Color(0xFF334155))

                    Text("Recommended Builds", color = Color(0xFFD4AF37), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    if (buildsList.isEmpty()) {
                        Text("No builds available", color = Color(0xFF64748B), fontSize = 11.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            buildsList.take(2).forEach { build ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = build.title.ifEmpty { "Build" },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (build.likes > 0) {
                                            Text(
                                                text = "❤ ${build.likes}",
                                                color = Color(0xFFEF4444),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (build.spell.isNotEmpty()) {
                                            Text(
                                                text = "⚡ ${build.spell}",
                                                color = Color(0xFF8B5CF6),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (build.emblem.isNotEmpty()) {
                                            val emblemImgUrl = "https://mlbb.io/images/emblem/main/${build.emblem.lowercase()}.png"
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                AsyncImage(
                                                    model = emblemImgUrl,
                                                    contentDescription = build.emblem,
                                                    modifier = Modifier.size(12.dp).clip(CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = build.emblem,
                                                    color = Color(0xFFF59E0B),
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    if (build.emblemTalents.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Talents:",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.horizontalScroll(rememberScrollState())
                                            ) {
                                                build.emblemTalents.forEach { talent ->
                                                    val talentImgUrl = "https://mlbb.io/images/emblem/ability/${talent.lowercase().replace(" ", "_")}.png"
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .background(Color(0xFF38BDF8).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        AsyncImage(
                                                            model = talentImgUrl,
                                                            contentDescription = talent,
                                                            modifier = Modifier.size(12.dp).clip(CircleShape)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = talent,
                                                            color = Color(0xFF38BDF8),
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        build.items.forEachIndexed { idx, item ->
                                            if (idx < 6) {
                                                ai.zasha.mlbbpicker.ui.components.ItemIcon(
                                                    itemName = item,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(38.dp)
                                                )
                                            }
                                        }
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

// ─── Meta Stats Tab Components ───────────────────────────────────────────────

@Composable
fun MetaStatsTabContent(
    metaStats: List<HeroMetaStats>,
    onHeroClick: (Hero) -> Unit,
    heroes: List<Hero>
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("winRate") } // "winRate", "pickRate", "banRate"

    val sortedStats = remember(metaStats, searchQuery, sortBy) {
        val filtered = if (searchQuery.isBlank()) {
            metaStats
        } else {
            metaStats.filter { it.heroName.contains(searchQuery, ignoreCase = true) }
        }
        when (sortBy) {
            "winRate" -> filtered.sortedByDescending { it.winRate }
            "pickRate" -> filtered.sortedByDescending { it.pickRate }
            "banRate" -> filtered.sortedByDescending { it.banRate }
            else -> filtered
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // Search & Filter
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search meta...", color = Color(0xFF94A3B8)) },
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

        Spacer(modifier = Modifier.height(10.dp))

        // Sort Selector Button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SortTabButton(
                label = "Win Rate",
                isActive = sortBy == "winRate",
                onClick = { sortBy = "winRate" },
                activeColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
            SortTabButton(
                label = "Pick Rate",
                isActive = sortBy == "pickRate",
                onClick = { sortBy = "pickRate" },
                activeColor = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
            SortTabButton(
                label = "Ban Rate",
                isActive = sortBy == "banRate",
                onClick = { sortBy = "banRate" },
                activeColor = Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Meta List
        if (sortedStats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data available", color = Color(0xFF64748B))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(sortedStats) { index, stat ->
                    val matchedHero = heroes.find { it.id == stat.heroId }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .border(
                                width = if (index < 3 && searchQuery.isEmpty()) 1.dp else 0.5.dp,
                                color = if (index < 3 && searchQuery.isEmpty()) {
                                    when (index) {
                                        0 -> Color(0xFFD4AF37) // Gold
                                        1 -> Color(0xFFC0C0C0) // Silver
                                        else -> Color(0xFFCD7F32) // Bronze
                                    }
                                } else Color(0xFF334155),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { matchedHero?.let { onHeroClick(it) } }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank Index Number
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    if (index < 3 && searchQuery.isEmpty()) {
                                        when (index) {
                                            0 -> Color(0xFFD4AF37).copy(alpha = 0.2f)
                                            1 -> Color(0xFFC0C0C0).copy(alpha = 0.2f)
                                            else -> Color(0xFFCD7F32).copy(alpha = 0.2f)
                                        }
                                    } else Color.Transparent,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (index < 3 && searchQuery.isEmpty()) {
                                    when (index) {
                                        0 -> Color(0xFFD4AF37)
                                        1 -> Color(0xFFC0C0C0)
                                        else -> Color(0xFFCD7F32)
                                    }
                                } else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Hero Avatar
                        AsyncImage(
                            model = stat.imgSrc,
                            contentDescription = stat.heroName,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF334155), CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Name + Role
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(
                                text = stat.heroName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stat.role.joinToString(", "),
                                color = Color(0xFFD4AF37),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Stats Column: WR, PR, BR
                        Row(
                            modifier = Modifier.weight(2f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatBox(label = "WR", value = "${String.format("%.1f", stat.winRate)}%", color = if (stat.winRate >= 50) Color(0xFF10B981) else Color(0xFFEF4444))
                            StatBox(label = "PR", value = "${String.format("%.1f", stat.pickRate)}%", color = Color(0xFF3B82F6))
                            StatBox(label = "BR", value = "${String.format("%.1f", stat.banRate)}%", color = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortTabButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isActive) activeColor.copy(alpha = 0.15f) else Color(0xFF1E293B),
                RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = if (isActive) activeColor else Color(0xFF334155),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) activeColor else Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 8.sp)
        Text(text = value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Premium Gating UI ────────────────────────────────────────────────────────

@Composable
fun PremiumLockOverlay(
    featureName: String,
    onUpgradeClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60F172A)), // Semi-transparent dark background
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFFD4AF37)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFD4AF37).copy(alpha = 0.1f), CircleShape)
                        .border(1.5.dp, Color(0xFFD4AF37), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked Feature",
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Unlock Pro Feature",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "$featureName is gated under MLBB Picker Pro.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onUpgradeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Upgrade to Pro",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── Solo Queue Recommendations Screen ────────────────────────────────────────

@Composable
fun SoloQueueScreen(
    heroes: List<Hero>,
    metaStats: List<HeroMetaStats>,
    buildRepository: BuildRepository
) {
    var selectedRole by remember { mutableStateOf<String?>(null) }
    val rankedHeroes = remember(heroes, metaStats, selectedRole) {
        val ranked = SoloQueueManager.computeSoloRankings(heroes, metaStats, buildRepository)
        if (selectedRole != null) {
            ranked.filter { it.role.any { r -> r.equals(selectedRole, true) } }
        } else {
            ranked
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Mode Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Solo Queue Rankings",
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ranked by carry index based on Win Rate, Pick Rate, and solo carry role weightings.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Role Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val roles = listOf(null to "All", "Fighter" to "Fighter", "Tank" to "Tank", "Mage" to "Mage", "Marksman" to "MM", "Assassin" to "Assassin", "Support" to "Support")
            roles.forEach { (role, label) ->
                val isSelected = selectedRole == role
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) Color(0xFFD4AF37) else Color(0xFF1E293B),
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFFD4AF37) else Color(0xFF334155),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedRole = role }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // List container with FAB overlay
        Box(modifier = Modifier.weight(1f)) {
            if (rankedHeroes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data available", color = Color(0xFF64748B))
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rankedHeroes) { rank ->
                        SoloHeroCard(rank = rank)
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        containerColor = Color(0xFFD4AF37),
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Scroll to Top",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SoloHeroCard(rank: SoloHeroRank) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(
            1.dp,
            when (rank.tier) {
                "S" -> Color(0xFFEF4444)
                "A" -> Color(0xFFF59E0B)
                "B" -> Color(0xFF3B82F6)
                else -> Color(0xFF334155)
            }
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tier Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            when (rank.tier) {
                                "S" -> Color(0xFFEF4444)
                                "A" -> Color(0xFFF59E0B)
                                "B" -> Color(0xFF3B82F6)
                                else -> Color(0xFF64748B)
                            },
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rank.tier,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Hero Avatar
                AsyncImage(
                    model = rank.imgSrc,
                    contentDescription = rank.heroName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0xFF334155), CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rank.heroName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = rank.role.joinToString(" / "),
                        color = Color(0xFFD4AF37),
                        fontSize = 10.sp
                    )
                }

                // Stats
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "WR: ${String.format("%.1f", rank.winRate)}%",
                        color = if (rank.winRate >= 50) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Score: ${String.format("%.1f", rank.soloScore)}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8)
                )
            }

            // Expanded Best Build Info
            if (expanded && rank.topBuild != null) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Best Solo Build: ${rank.topBuild.title}",
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Items Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rank.topBuild.items.take(6).forEach { item ->
                        ai.zasha.mlbbpicker.ui.components.ItemIcon(
                            itemName = item,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        )
                    }
                }
            }
        }
    }
}

