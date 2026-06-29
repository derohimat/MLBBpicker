package ai.zasha.mlbbpicker.service

import android.annotation.SuppressLint
import android.content.Context
import ai.zasha.mlbbpicker.data.*
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import ai.zasha.mlbbpicker.data.*
import ai.zasha.mlbbpicker.theme.MLBBPickerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

// Custom Lifecycle Owner for WindowManager Compose Views
class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)

    init {
        controller.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
}

class OverlayViewManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val heroRepository = HeroRepository(context)
    private val metaStatsRepository = MetaStatsRepository(context)
    private val buildRepository = BuildRepository(context)

    private var bubbleView: View? = null
    private var panelView: View? = null

    private var bubbleLifecycleOwner: OverlayLifecycleOwner? = null
    private var panelLifecycleOwner: OverlayLifecycleOwner? = null

    private var isExpanded = false
    private var isShowing = false

    // Keep track of bubble location
    private var bubbleX = 100
    private var bubbleY = 300

    var isManuallyDismissed = false

    // Delegate draft state to central DraftManager
    private val selectedEnemies get() = DraftManager.selectedEnemies
    private val selectedAllies get() = DraftManager.selectedAllies
    private val counterSuggestions get() = DraftManager.counterSuggestions
    private val synergySuggestions get() = DraftManager.synergySuggestions
    private val banRecommendations get() = DraftManager.banRecommendations

    private val metaStats = mutableStateListOf<HeroMetaStats>()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        // Load meta stats on init
        coroutineScope.launch {
            val stats = metaStatsRepository.getMetaStats()
            metaStats.clear()
            metaStats.addAll(stats)
            // Generate initial ban recommendations
            val bans = BanHelper.getRecommendedBans(stats)
            banRecommendations.clear()
            banRecommendations.addAll(bans)
        }
    }

    fun showOverlay(byUserTrigger: Boolean = false) {
        if (byUserTrigger) {
            isManuallyDismissed = false
        }
        if (isManuallyDismissed) return
        if (isShowing) return
        isShowing = true
        
        // Reload repositories to pick up any new OTA patches
        heroRepository.reload()
        metaStatsRepository.reload()
        buildRepository.reload()

        // Refresh meta stats and ban recommendations from new patch
        coroutineScope.launch {
            val stats = metaStatsRepository.getMetaStats()
            metaStats.clear()
            metaStats.addAll(stats)
            val bans = BanHelper.getRecommendedBans(stats)
            banRecommendations.clear()
            banRecommendations.addAll(bans)
            updateRecommendations()
        }

        if (isExpanded) {
            showPanel()
        } else {
            showBubble()
        }
    }

    fun hideOverlay(manually: Boolean = false) {
        if (manually) {
            isManuallyDismissed = true
        }
        if (!isShowing) return
        isShowing = false
        removeBubble()
        removePanel()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        removePanel()
        removeBubble()

        val lifecycleOwner = OverlayLifecycleOwner()
        bubbleLifecycleOwner = lifecycleOwner

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MLBBPickerTheme {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF1E2A4A), Color(0xFF0F172A))
                                ),
                                shape = CircleShape
                            )
                            .border(2.dp, Color(0xFFD4AF37), CircleShape) // Gold border
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = ai.zasha.mlbbpicker.R.mipmap.ic_launcher),
                            contentDescription = "MLBB Picker Launcher Icon",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        lifecycleOwner.start()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }

        // Setup touch listener for dragging, clicking, and long-pressing to close
        composeView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchTime = 0L
            private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
            private var isLongPressed = false
            private val longPressRunnable = Runnable {
                isLongPressed = true
                android.widget.Toast.makeText(context, "Draft Assistant dismissed", android.widget.Toast.LENGTH_SHORT).show()
                hideOverlay(manually = true)
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchTime = System.currentTimeMillis()
                        isLongPressed = false
                        longPressHandler.postDelayed(longPressRunnable, 600) // 600ms long press
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (diffX > 10 || diffY > 10) {
                            longPressHandler.removeCallbacks(longPressRunnable)
                        }
                        if (!isLongPressed) {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(composeView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        if (isLongPressed) return true
                        
                        bubbleX = params.x
                        bubbleY = params.y
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        val duration = System.currentTimeMillis() - touchTime
                        if (diffX < 10 && diffY < 10 && duration < 300) {
                            // Tapped! Expand to panel
                            isExpanded = true
                            showPanel()
                        }
                        return true
                    }
                }
                return false
            }
        })

        bubbleView = composeView
        windowManager.addView(composeView, params)
    }

    private fun showPanel() {
        removeBubble()
        removePanel()

        val lifecycleOwner = OverlayLifecycleOwner()
        panelLifecycleOwner = lifecycleOwner

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MLBBPickerTheme {
                    OverlayPanelContent(
                        heroes = heroRepository.heroes,
                        selectedEnemies = selectedEnemies,
                        selectedAllies = selectedAllies,
                        counterSuggestions = counterSuggestions,
                        synergySuggestions = synergySuggestions,
                        metaStats = metaStats,
                        banRecommendations = banRecommendations,
                        buildRepository = buildRepository,
                        onCollapse = {
                            isExpanded = false
                            showBubble()
                        },
                        onClearAll = {
                            selectedEnemies.indices.forEach { selectedEnemies[it] = null }
                            selectedAllies.indices.forEach { selectedAllies[it] = null }
                            counterSuggestions.clear()
                            synergySuggestions.clear()
                        },
                        onUpdateRecommendations = {
                            updateRecommendations()
                        },
                        onSwapSlots = { fromType, fromIdx, toType, toIdx ->
                            swapSlots(fromType, fromIdx, toType, toIdx)
                        },
                        onSelectHero = { slotType, index, hero ->
                            if (slotType == "enemy") {
                                selectedEnemies[index] = hero
                            } else {
                                selectedAllies[index] = hero
                            }
                            updateRecommendations()
                        },
                        onDismiss = {
                            hideOverlay(manually = true)
                        }
                    )
                }
            }
        }

        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        lifecycleOwner.start()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Make panel focusable so that search keyboard functions
        val params = WindowManager.LayoutParams(
            dpToPx(340),
            dpToPx(520),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        panelView = composeView
        windowManager.addView(composeView, params)
    }

    private fun updateRecommendations() {
        DraftManager.updateRecommendations(coroutineScope, heroRepository, metaStats)
    }

    /** Swap heroes between two slots (supports cross-team swapping) */
    private fun swapSlots(fromType: String, fromIdx: Int, toType: String, toIdx: Int) {
        DraftManager.swapSlots(fromType, fromIdx, toType, toIdx)
        updateRecommendations()
    }

    private fun removeBubble() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        bubbleView = null
        bubbleLifecycleOwner?.destroy()
        bubbleLifecycleOwner = null
    }

    private fun removePanel() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        panelView = null
        panelLifecycleOwner?.destroy()
        panelLifecycleOwner = null
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}

// ─── Panel Content ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayPanelContent(
    heroes: List<Hero>,
    selectedEnemies: List<Hero?>,
    selectedAllies: List<Hero?>,
    counterSuggestions: List<CounterSuggestion>,
    synergySuggestions: List<SynergySuggestion>,
    metaStats: List<HeroMetaStats>,
    banRecommendations: List<BanRecommendation> = emptyList(),
    buildRepository: BuildRepository,
    isFullScreen: Boolean = false,
    onCollapse: () -> Unit,
    onClearAll: () -> Unit,
    onUpdateRecommendations: () -> Unit,
    onSwapSlots: (String, Int, String, Int) -> Unit,
    onSelectHero: (String, Int, Hero?) -> Unit,
    onDismiss: () -> Unit
) {
    var activeSlotType by remember { mutableStateOf<String?>(null) } // "enemy" or "ally"
    var activeSlotIndex by remember { mutableStateOf(-1) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<String?>(null) }

    // Panel tabs: 0=Draft, 1=Bans, 2=Build
    var activePanel by remember { mutableIntStateOf(0) }

    // Quick-swap state
    var swapMode by remember { mutableStateOf(false) }
    var swapFromType by remember { mutableStateOf<String?>(null) }
    var swapFromIndex by remember { mutableIntStateOf(-1) }

    // Build detail state
    var buildDetailHeroId by remember { mutableStateOf<Int?>(null) }

    // Meta stats map for quick lookup
    val metaStatsMap = remember(metaStats) {
        metaStats.associateBy { it.heroId }
    }

    val filteredHeroes = remember(searchQuery, heroes) {
        if (searchQuery.isBlank()) {
            heroes
        } else {
            heroes.filter { it.hero_name.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Team analysis computed from allies
    val teamAnalysis = remember(selectedAllies.toList()) {
        TeamAnalyzer.analyze(selectedAllies)
    }

    // Filter suggestions by role
    val filteredCounters = remember(counterSuggestions.toList(), selectedRoleFilter) {
        if (selectedRoleFilter == null) counterSuggestions
        else counterSuggestions.filter { cs -> cs.role.any { it.equals(selectedRoleFilter, true) } }
    }

    val filteredSynergies = remember(synergySuggestions.toList(), selectedRoleFilter) {
        if (selectedRoleFilter == null) synergySuggestions
        else synergySuggestions.filter { ss -> ss.role.any { it.equals(selectedRoleFilter, true) } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isFullScreen) {
                    Modifier.background(Color(0xFF0F172A))
                } else {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE60F172A))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                }
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Draft Assistant",
                    color = Color(0xFFF1F5F9),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Solo Mode Chip
                    val isSolo = DraftManager.isSoloMode
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSolo) Color(0xFFD4AF37) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (isSolo) Color(0xFFD4AF37) else Color(0xFF475569),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                DraftManager.isSoloMode = !DraftManager.isSoloMode
                                onUpdateRecommendations()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isSolo) Color.Black else Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Solo",
                                color = if (isSolo) Color.Black else Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onClearAll, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (!isFullScreen) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                tint = Color(0xFFF1F5F9),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss Overlay",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Build Detail View
            if (buildDetailHeroId != null) {
                BuildDetailView(
                    heroId = buildDetailHeroId!!,
                    heroes = heroes,
                    buildRepository = buildRepository,
                    metaStatsMap = metaStatsMap,
                    onBack = { buildDetailHeroId = null }
                )
                return@Column
            }

            // Hero Selection View
            if (activeSlotType != null) {
                val currentHeroId = if (activeSlotType == "enemy") {
                    selectedEnemies.getOrNull(activeSlotIndex)?.id
                } else {
                    selectedAllies.getOrNull(activeSlotIndex)?.id
                }
                val selectedHeroIds = (selectedEnemies + selectedAllies)
                    .filterNotNull()
                    .map { it.id }
                    .filter { it != currentHeroId }
                    .toSet()

                HeroSelectionView(
                    activeSlotType = activeSlotType!!,
                    activeSlotIndex = activeSlotIndex,
                    heroes = filteredHeroes,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    metaStatsMap = metaStatsMap,
                    selectedHeroIds = selectedHeroIds,
                    counterSuggestions = counterSuggestions,
                    synergySuggestions = synergySuggestions,
                    banRecommendations = banRecommendations.map { it.heroId }.toSet(),
                    selectedRoleFilter = selectedRoleFilter,
                    onRoleSelected = { selectedRoleFilter = it },
                    isFullScreen = isFullScreen,
                    initialFilter = when (activePanel) {
                        0 -> "Counter"
                        1 -> "Synergy"
                        2 -> "Ban"
                        else -> "All"
                    },
                    onSelectHero = { hero ->
                        onSelectHero(activeSlotType!!, activeSlotIndex, hero)
                        activeSlotType = null
                        activeSlotIndex = -1
                    },
                    onRemove = {
                        onSelectHero(activeSlotType!!, activeSlotIndex, null)
                        activeSlotType = null
                        activeSlotIndex = -1
                    },
                    onCancel = {
                        activeSlotType = null
                        activeSlotIndex = -1
                    }
                )
                return@Column
            }

            // ─── Main Draft Board ─────────────────────────────────────────

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                // Enemies Row
                Text(
                    text = "Enemy Heroes",
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in 0 until 5) {
                        HeroSlot(
                            hero = selectedEnemies[i],
                            isEnemy = true,
                            isSwapHighlight = swapMode && !(swapFromType == "enemy" && swapFromIndex == i),
                            onClick = {
                                if (swapMode) {
                                    // Execute swap
                                    onSwapSlots(swapFromType!!, swapFromIndex, "enemy", i)
                                    swapMode = false
                                    swapFromType = null
                                    swapFromIndex = -1
                                } else {
                                    activeSlotType = "enemy"
                                    activeSlotIndex = i
                                    searchQuery = ""
                                }
                            },
                            onLongClick = {
                                if (selectedEnemies[i] != null) {
                                    swapMode = true
                                    swapFromType = "enemy"
                                    swapFromIndex = i
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Allies Row + Team Analysis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ally Heroes",
                        color = Color(0xFF3B82F6),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Team score badge
                    if (selectedAllies.any { it != null }) {
                        TeamScoreBadge(teamAnalysis)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in 0 until 5) {
                        HeroSlot(
                            hero = selectedAllies[i],
                            isEnemy = false,
                            isSwapHighlight = swapMode && !(swapFromType == "ally" && swapFromIndex == i),
                            onClick = {
                                if (swapMode) {
                                    onSwapSlots(swapFromType!!, swapFromIndex, "ally", i)
                                    swapMode = false
                                    swapFromType = null
                                    swapFromIndex = -1
                                } else {
                                    activeSlotType = "ally"
                                    activeSlotIndex = i
                                    searchQuery = ""
                                }
                            },
                            onLongClick = {
                                if (selectedAllies[i] != null) {
                                    swapMode = true
                                    swapFromType = "ally"
                                    swapFromIndex = i
                                }
                            }
                        )
                    }
                }

                // Swap mode indicator
                if (swapMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color(0x33D4AF37), RoundedCornerShape(6.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tap target slot to swap", color = Color(0xFFD4AF37), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Cancel",
                            color = Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                swapMode = false
                                swapFromType = null
                                swapFromIndex = -1
                            }
                        )
                    }
                }

                // Team Analysis Warnings (compact)
                if (teamAnalysis.warnings.isNotEmpty() && selectedAllies.any { it != null }) {
                    TeamWarningRow(teamAnalysis)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ─── Panel Tabs ───────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PanelTab("Counter", activePanel == 0, Color(0xFFEF4444)) { activePanel = 0 }
                    PanelTab("Synergy", activePanel == 1, Color(0xFF3B82F6)) { activePanel = 1 }
                    PanelTab("Bans", activePanel == 2, Color(0xFFF59E0B)) { activePanel = 2 }
                }

                // ─── Role Filter Chips ────────────────────────────────────
                if (activePanel < 2) {
                    RoleFilterChips(
                        selectedRole = selectedRoleFilter,
                        onRoleSelected = { selectedRoleFilter = it }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ─── Panel Content ────────────────────────────────────────
                when (activePanel) {
                    0 -> CounterPanel(filteredCounters, metaStatsMap) { heroId -> buildDetailHeroId = heroId }
                    1 -> SynergyPanel(filteredSynergies, metaStatsMap) { heroId -> buildDetailHeroId = heroId }
                    2 -> BanPanel(banRecommendations)
                }
            }
        }
    }
}

// ─── Hero Selection View ─────────────────────────────────────────────────────

@Composable
private fun PickerFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) Color(0xFFD4AF37) else Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFFD4AF37) else Color(0xFF334155),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color(0xFF94A3B8),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HeroSelectionView(
    activeSlotType: String,
    activeSlotIndex: Int,
    heroes: List<Hero>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    metaStatsMap: Map<Int, HeroMetaStats>,
    selectedHeroIds: Set<Int>,
    counterSuggestions: List<CounterSuggestion>,
    synergySuggestions: List<SynergySuggestion>,
    banRecommendations: Set<Int>,
    selectedRoleFilter: String?,
    onRoleSelected: (String?) -> Unit,
    isFullScreen: Boolean = false,
    initialFilter: String,
    onSelectHero: (Hero) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit
) {
    val resolvedInitialFilter = remember(initialFilter, counterSuggestions, synergySuggestions, banRecommendations) {
        when (initialFilter) {
            "Counter" -> if (counterSuggestions.isNotEmpty()) "Counter" else "All"
            "Synergy" -> if (synergySuggestions.isNotEmpty()) "Synergy" else "All"
            "Ban" -> if (banRecommendations.isNotEmpty()) "Ban" else "All"
            else -> "All"
        }
    }
    var pickerFilter by remember(resolvedInitialFilter) { mutableStateOf(resolvedInitialFilter) }

    val displayedHeroes = remember(heroes, pickerFilter, counterSuggestions, synergySuggestions, banRecommendations, selectedRoleFilter) {
        var list = when (pickerFilter) {
            "Counter" -> {
                val counterIds = counterSuggestions.map { it.id }.toSet()
                heroes.filter { counterIds.contains(it.id) }
            }
            "Synergy" -> {
                val synergyIds = synergySuggestions.map { it.id }.toSet()
                heroes.filter { synergyIds.contains(it.id) }
            }
            "Ban" -> {
                heroes.filter { banRecommendations.contains(it.id) }
            }
            else -> heroes
        }
        if (selectedRoleFilter != null) {
            list = list.filter { it.roleList.any { r -> r.equals(selectedRoleFilter, true) } }
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select ${if (activeSlotType == "enemy") "Enemy" else "Ally"} #${activeSlotIndex + 1}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onRemove,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Remove", fontSize = 11.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Picker Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PickerFilterChip("All", pickerFilter == "All") { pickerFilter = "All" }
            if (counterSuggestions.isNotEmpty()) {
                PickerFilterChip("Counters", pickerFilter == "Counter") { pickerFilter = "Counter" }
            }
            if (synergySuggestions.isNotEmpty()) {
                PickerFilterChip("Synergies", pickerFilter == "Synergy") { pickerFilter = "Synergy" }
            }
            if (banRecommendations.isNotEmpty()) {
                PickerFilterChip("Bans", pickerFilter == "Ban") { pickerFilter = "Ban" }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Role Filter Chips
        RoleFilterChips(
            selectedRole = selectedRoleFilter,
            onRoleSelected = onRoleSelected
        )

        Spacer(modifier = Modifier.height(6.dp))

        TextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search hero...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isFullScreen) 5 else 3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayedHeroes) { hero ->
                val stats = metaStatsMap[hero.id]
                val isAlreadySelected = selectedHeroIds.contains(hero.id)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .then(
                            if (isAlreadySelected) {
                                Modifier.alpha(0.3f)
                            } else {
                                Modifier.clickable { onSelectHero(hero) }
                            }
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        AsyncImage(
                            model = hero.img_src,
                            contentDescription = hero.hero_name,
                            modifier = Modifier
                                .size(if (isFullScreen) 52.dp else 48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155)),
                            contentScale = ContentScale.Crop
                        )
                        // Win rate mini badge
                        if (stats != null) {
                            val wrColor = when {
                                stats.winRate >= 53 -> Color(0xFF10B981)
                                stats.winRate >= 50 -> Color(0xFF3B82F6)
                                else -> Color(0xFFEF4444)
                            }
                            Text(
                                text = "${String.format("%.0f", stats.winRate)}%",
                                color = wrColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0xCC0F172A), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = hero.hero_name,
                        color = Color.White,
                        fontSize = if (isFullScreen) 10.sp else 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
        ) {
            Text("Cancel", color = Color.White)
        }
    }
}

// ─── Build Detail View ───────────────────────────────────────────────────────

@Composable
private fun BuildDetailView(
    heroId: Int,
    heroes: List<Hero>,
    buildRepository: BuildRepository,
    metaStatsMap: Map<Int, HeroMetaStats>,
    onBack: () -> Unit
) {
    val hero = heroes.find { it.id == heroId }
    val builds = remember(heroId) { buildRepository.getBuildsForHero(heroId) }
    val stats = metaStatsMap[heroId]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hero != null) {
                AsyncImage(
                    model = hero.img_src,
                    contentDescription = hero.hero_name,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(hero.hero_name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row {
                        Text(hero.role, color = Color(0xFFD4AF37), fontSize = 10.sp)
                        if (stats != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "WR: ${String.format("%.1f", stats.winRate)}%",
                                color = if (stats.winRate >= 50) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Back", fontSize = 11.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (builds.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No builds available", color = Color(0xFF64748B), fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(builds) { build ->
                    BuildCard(build)
                }
            }
        }
    }
}

@Composable
private fun BuildCard(build: HeroBuild) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        // Title + likes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = build.title.ifEmpty { "Build" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
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

        Spacer(modifier = Modifier.height(6.dp))

        // Spell + Emblem row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (build.spell.isNotEmpty()) {
                InfoChip("⚡ ${build.spell}", Color(0xFF8B5CF6))
            }
            if (build.emblem.isNotEmpty()) {
                InfoChip("🏅 ${build.emblem}", Color(0xFFF59E0B))
            }
        }

        if (build.emblemTalents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Talents: ${build.emblemTalents.joinToString(" · ")}",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Items
        Text("Items:", color = Color(0xFF94A3B8), fontSize = 9.sp)
        Spacer(modifier = Modifier.height(4.dp))
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
                            .height(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ─── Panel Tabs & Role Filter ────────────────────────────────────────────────

@Composable
private fun RowScope.PanelTab(label: String, isActive: Boolean, activeColor: Color, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        if (isActive) activeColor.copy(alpha = 0.2f) else Color.Transparent,
        label = "tabBg"
    )
    val borderColor by animateColorAsState(
        if (isActive) activeColor else Color(0xFF334155),
        label = "tabBorder"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .background(bgColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) activeColor else Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun RoleFilterChips(selectedRole: String?, onRoleSelected: (String?) -> Unit) {
    val roles = listOf("All", "Tank", "Fighter", "Assassin", "Mage", "Marksman", "Support")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        roles.forEach { role ->
            val isSelected = (role == "All" && selectedRole == null) || role == selectedRole
            val chipColor = when (role) {
                "Tank" -> Color(0xFF6366F1)
                "Fighter" -> Color(0xFFF97316)
                "Assassin" -> Color(0xFFEF4444)
                "Mage" -> Color(0xFF8B5CF6)
                "Marksman" -> Color(0xFF10B981)
                "Support" -> Color(0xFF06B6D4)
                else -> Color(0xFF94A3B8)
            }

            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) chipColor.copy(alpha = 0.25f) else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        0.5.dp,
                        if (isSelected) chipColor else Color(0xFF475569),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        onRoleSelected(if (role == "All") null else role)
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = role,
                    color = if (isSelected) chipColor else Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ─── Counter Panel ───────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.CounterPanel(
    suggestions: List<CounterSuggestion>,
    metaStatsMap: Map<Int, HeroMetaStats>,
    onHeroClick: (Int) -> Unit
) {
    if (suggestions.isEmpty()) {
        EmptyListHint("Select enemies to see counters")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            items(suggestions) { item ->
                val stats = metaStatsMap[item.id]
                RecommendationRow(
                    name = item.heroName,
                    imgUrl = item.imgSrc,
                    scoreText = String.format("%.1f", item.score),
                    tier = item.tier,
                    winRate = stats?.winRate,
                    isMetaPick = stats != null && stats.winRate >= 52.0,
                    onClick = { onHeroClick(item.id) }
                )
            }
        }
    }
}

// ─── Synergy Panel ───────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.SynergyPanel(
    suggestions: List<SynergySuggestion>,
    metaStatsMap: Map<Int, HeroMetaStats>,
    onHeroClick: (Int) -> Unit
) {
    if (suggestions.isEmpty()) {
        EmptyListHint("Select allies to see synergies")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            items(suggestions) { item ->
                val synergyScore = item.score ?: item.synergyHeroes.firstOrNull()?.realScore ?: 0.0
                val stats = metaStatsMap[item.id]
                RecommendationRow(
                    name = item.heroName,
                    imgUrl = item.imgSrc,
                    scoreText = String.format("%.1f", synergyScore),
                    tier = "",
                    winRate = stats?.winRate,
                    isMetaPick = stats != null && stats.winRate >= 52.0,
                    onClick = { onHeroClick(item.id) }
                )
            }
        }
    }
}

// ─── Ban Panel ───────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.BanPanel(bans: List<BanRecommendation>) {
    if (bans.isEmpty()) {
        EmptyListHint("Loading ban suggestions...")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            items(bans) { ban ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp, horizontal = 2.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ban.imgSrc,
                        contentDescription = ban.heroName,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ban.heroName,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = ban.reason,
                            color = Color(0xFFF59E0B),
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "WR ${String.format("%.1f", ban.winRate)}%",
                            color = if (ban.winRate >= 52) Color(0xFF10B981) else Color(0xFF94A3B8),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "BR ${String.format("%.1f", ban.banRate)}%",
                            color = Color(0xFFEF4444),
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Team Analysis Components ────────────────────────────────────────────────

@Composable
private fun TeamScoreBadge(analysis: TeamAnalysis) {
    val scoreColor = when {
        analysis.overallScore >= 75 -> Color(0xFF10B981)
        analysis.overallScore >= 50 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(scoreColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(0.5.dp, scoreColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${analysis.overallScore}",
            color = scoreColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "/100",
            color = scoreColor.copy(alpha = 0.6f),
            fontSize = 8.sp
        )
    }
}

@Composable
private fun TeamWarningRow(analysis: TeamAnalysis) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        analysis.warnings.forEach { warning ->
            val (bgColor, textColor) = when (warning.severity) {
                WarningSeverity.CRITICAL -> Color(0x33EF4444) to Color(0xFFEF4444)
                WarningSeverity.WARNING -> Color(0x33F59E0B) to Color(0xFFF59E0B)
                WarningSeverity.INFO -> Color(0x3310B981) to Color(0xFF10B981)
            }
            Text(
                text = warning.message,
                color = textColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(bgColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ─── Shared Components ───────────────────────────────────────────────────────

@Composable
fun HeroSlot(
    hero: Hero?,
    isEnemy: Boolean,
    isSwapHighlight: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val borderColor = when {
        isSwapHighlight -> Color(0xFFD4AF37)
        isEnemy -> Color(0xFFEF4444)
        else -> Color(0xFF3B82F6)
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (isSwapHighlight) Color(0xFF2A2000) else Color(0xFF1E293B))
            .border(
                width = if (isSwapHighlight) 2.5.dp else 2.dp,
                color = borderColor,
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (hero != null) {
            AsyncImage(
                model = hero.img_src,
                contentDescription = hero.hero_name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = "+",
                color = Color(0xFF64748B),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RecommendationRow(
    name: String,
    imgUrl: String,
    scoreText: String,
    tier: String,
    winRate: Double? = null,
    isMetaPick: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 2.dp)
            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = imgUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            if (isMetaPick) {
                Text(
                    text = "🔥",
                    fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    text = "Score: $scoreText",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp
                )
                if (winRate != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WR ${String.format("%.0f", winRate)}%",
                        color = if (winRate >= 50) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (tier.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .background(
                        when (tier) {
                            "SS" -> Color(0xFFEF4444)
                            "S" -> Color(0xFFF59E0B)
                            "A" -> Color(0xFF8B5CF6)
                            else -> Color(0xFF64748B)
                        },
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = tier,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyListHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
