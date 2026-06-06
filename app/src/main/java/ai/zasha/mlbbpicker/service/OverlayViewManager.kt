package ai.zasha.mlbbpicker.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
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
import ai.zasha.mlbbpicker.data.CounterSuggestion
import ai.zasha.mlbbpicker.data.Hero
import ai.zasha.mlbbpicker.data.HeroRepository
import ai.zasha.mlbbpicker.data.SynergySuggestion
import ai.zasha.mlbbpicker.theme.MLBBPickerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    private val repository = HeroRepository(context)

    private var bubbleView: View? = null
    private var panelView: View? = null

    private var bubbleLifecycleOwner: OverlayLifecycleOwner? = null
    private var panelLifecycleOwner: OverlayLifecycleOwner? = null

    private var isExpanded = false
    private var isShowing = false

    // Keep track of bubble location
    private var bubbleX = 100
    private var bubbleY = 300

    // State holders for draft
    private val selectedEnemies = mutableStateListOf<Hero?>().apply { repeat(5) { add(null) } }
    private val selectedAllies = mutableStateListOf<Hero?>().apply { repeat(5) { add(null) } }

    private val counterSuggestions = mutableStateListOf<CounterSuggestion>()
    private val synergySuggestions = mutableStateListOf<SynergySuggestion>()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun showOverlay() {
        if (isShowing) return
        isShowing = true
        if (isExpanded) {
            showPanel()
        } else {
            showBubble()
        }
    }

    fun hideOverlay() {
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
                        Text(
                            text = "MLBB",
                            color = Color(0xFFD4AF37),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
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

        // Setup touch listener for dragging and clicking
        composeView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(composeView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
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
                        heroes = repository.heroes,
                        selectedEnemies = selectedEnemies,
                        selectedAllies = selectedAllies,
                        counterSuggestions = counterSuggestions,
                        synergySuggestions = synergySuggestions,
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
            dpToPx(480),
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
        val enemies = selectedEnemies.filterNotNull().map { it.id }
        val allies = selectedAllies.filterNotNull().map { it.id }

        coroutineScope.launch {
            if (enemies.isNotEmpty()) {
                val counters = repository.getCounterSuggestions(enemies)
                counterSuggestions.clear()
                counterSuggestions.addAll(counters.take(15))
            } else {
                counterSuggestions.clear()
            }

            if (allies.isNotEmpty()) {
                val synergies = repository.getSynergySuggestions(allies)
                synergySuggestions.clear()
                synergySuggestions.addAll(synergies.take(15))
            } else {
                synergySuggestions.clear()
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayPanelContent(
    heroes: List<Hero>,
    selectedEnemies: List<Hero?>,
    selectedAllies: List<Hero?>,
    counterSuggestions: List<CounterSuggestion>,
    synergySuggestions: List<SynergySuggestion>,
    onCollapse: () -> Unit,
    onClearAll: () -> Unit,
    onUpdateRecommendations: () -> Unit
) {
    var activeSlotType by remember { mutableStateOf<String?>(null) } // "enemy" or "ally"
    var activeSlotIndex by remember { mutableStateOf(-1) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredHeroes = remember(searchQuery, heroes) {
        if (searchQuery.isBlank()) {
            heroes
        } else {
            heroes.filter { it.hero_name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE60F172A)) // Semi-transparent Slate 900
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
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
                    IconButton(onClick = onClearAll, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Collapse",
                            tint = Color(0xFFF1F5F9),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (activeSlotType == null) {
                // Main View (Draft Board & Recommendations)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    // Enemies Row
                    Text(
                        text = "Enemy Heroes",
                        color = Color(0xFFEF4444), // Red
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
                                onClick = {
                                    activeSlotType = "enemy"
                                    activeSlotIndex = i
                                    searchQuery = ""
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Allies Row
                    Text(
                        text = "Ally Heroes",
                        color = Color(0xFF3B82F6), // Blue
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
                                hero = selectedAllies[i],
                                isEnemy = false,
                                onClick = {
                                    activeSlotType = "ally"
                                    activeSlotIndex = i
                                    searchQuery = ""
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Recommendations Lists
                    Row(modifier = Modifier.weight(1f)) {
                        // Counters Panel
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(end = 4.dp)
                        ) {
                            Text(
                                text = "Counters",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (counterSuggestions.isEmpty()) {
                                EmptyListHint("Select enemies")
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    items(counterSuggestions) { item ->
                                        RecommendationRow(
                                            name = item.heroName,
                                            imgUrl = item.imgSrc,
                                            scoreText = String.format("%.1f", item.score),
                                            tier = item.tier
                                        )
                                    }
                                }
                            }
                        }

                        // Synergies Panel
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 4.dp)
                        ) {
                            Text(
                                text = "Synergies",
                                color = Color(0xFF3B82F6),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (synergySuggestions.isEmpty()) {
                                EmptyListHint("Select allies")
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    items(synergySuggestions) { item ->
                                        val synergyScore = item.score ?: item.synergyHeroes.firstOrNull()?.realScore ?: 0.0
                                        RecommendationRow(
                                            name = item.heroName,
                                            imgUrl = item.imgSrc,
                                            scoreText = String.format("%.1f", synergyScore),
                                            tier = ""
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Hero Selection View
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
                            onClick = {
                                if (activeSlotType == "enemy") {
                                    (selectedEnemies as MutableList<Hero?>)[activeSlotIndex] = null
                                } else {
                                    (selectedAllies as MutableList<Hero?>)[activeSlotIndex] = null
                                }
                                activeSlotType = null
                                activeSlotIndex = -1
                                onUpdateRecommendations()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Remove", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
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
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredHeroes) { hero ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (activeSlotType == "enemy") {
                                            (selectedEnemies as MutableList<Hero?>)[activeSlotIndex] = hero
                                        } else {
                                            (selectedAllies as MutableList<Hero?>)[activeSlotIndex] = hero
                                        }
                                        activeSlotType = null
                                        activeSlotIndex = -1
                                        onUpdateRecommendations()
                                    }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = hero.img_src,
                                    contentDescription = hero.hero_name,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = hero.hero_name,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            activeSlotType = null
                            activeSlotIndex = -1
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun HeroSlot(
    hero: Hero?,
    isEnemy: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isEnemy) Color(0xFFEF4444) else Color(0xFF3B82F6)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E293B))
            .border(2.dp, borderColor, CircleShape)
            .clickable { onClick() },
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
    tier: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imgUrl,
            contentDescription = name,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
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
            Text(
                text = "Score: $scoreText",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp
            )
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
