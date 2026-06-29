package ai.zasha.mlbbpicker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ItemIcon(
    itemName: String,
    modifier: Modifier = Modifier
) {
    // Generate clean initials from the item name. E.g. "Warrior Boots" -> "WB", "Blade of Despair" -> "BOD"
    val initials = itemName
        .split(" ")
        .filter { it.isNotEmpty() && it.lowercase() != "of" && it.lowercase() != "the" && it.lowercase() != "and" }
        .mapNotNull { it.firstOrNull() }
        .joinToString("")
        .take(3)
        .uppercase()

    // Categorize item to assign consistent color palettes
    val lower = itemName.lowercase()
    val gradient = when {
        // Movement/Speed
        lower.contains("boots") || lower.contains("shoes") || lower.contains("windtalker") || lower.contains("feather") -> {
            Brush.verticalGradient(
                colors = listOf(Color(0xFFB45309), Color(0xFF78350F)) // Warm brown/gold
            )
        }
        // Magic
        lower.contains("wand") || lower.contains("talisman") || lower.contains("glowing") || 
        lower.contains("genius") || lower.contains("holy") || lower.contains("crystal") || 
        lower.contains("glaive") || lower.contains("truncheon") || lower.contains("wings") || 
        lower.contains("tome") || lower.contains("necklace") || lower.contains("energy") -> {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF7C3AED), Color(0xFF4C1D95)) // Deep Purple/Indigo
            )
        }
        // Defense/Shield
        lower.contains("armor") || lower.contains("shield") || lower.contains("cuirass") || 
        lower.contains("helmet") || lower.contains("immortality") || lower.contains("brute") || 
        lower.contains("twilight") || lower.contains("dominance") || lower.contains("guardian") ||
        lower.contains("oracle") || lower.contains("antique") || lower.contains("belt") -> {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF059669), Color(0xFF064E3B)) // Emerald green
            )
        }
        // Physical Attack / Offense
        else -> {
            Brush.verticalGradient(
                colors = listOf(Color(0xFFDC2626), Color(0xFF7F1D1D)) // Crimson red
            )
        }
    }

    val borderStrokeColor = when {
        lower.contains("boots") || lower.contains("shoes") -> Color(0xFFF59E0B)
        lower.contains("wand") || lower.contains("talisman") -> Color(0xFFA78BFA)
        lower.contains("armor") || lower.contains("shield") -> Color(0xFF34D399)
        else -> Color(0xFFF87171)
    }

    Box(
        modifier = modifier
            .background(gradient, RoundedCornerShape(8.dp))
            .border(1.dp, borderStrokeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = itemName,
                color = Color(0xFFE2E8F0),
                fontSize = 7.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                lineHeight = 8.sp
            )
        }
    }
}
