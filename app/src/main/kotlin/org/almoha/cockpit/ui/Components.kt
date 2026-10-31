package org.almoha.cockpit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.almoha.cockpit.data.AppEntry
import org.almoha.cockpit.data.ContactFav
import org.almoha.cockpit.media.MediaUiState

@Composable
fun Panel(
    accent: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0f to accent.copy(alpha = 0.16f),
                    0.45f to CockpitColors.Card,
                    1f to Color(0xFF090E14),
                ),
            )
            .border(1.dp, CockpitColors.Stroke, shape),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun SectionLabel(
    icon: ImageVector,
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            color = CockpitColors.TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            maxLines = 1,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
fun QuickAction(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(50.dp)
            .clip(shape)
            .background(accent.copy(alpha = 0.13f))
            .border(1.dp, accent.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = CockpitColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


@Composable
fun StatusIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    accent: Color = CockpitColors.Accent,
    size: Dp = 46.dp,
    iconSize: Dp = 21.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (active) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) accent else CockpitColors.TextDim,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    filled: Boolean = false,
    accent: Color = Color.White,
    enabled: Boolean = true,
) {
    // Boutons cerclés et teintés : bien visibles sur le fond sombre, même d'un coup d'œil.
    val background = when {
        filled -> if (enabled) Color.White else Color.White.copy(alpha = 0.25f)
        enabled -> accent.copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    val tint = when {
        filled && enabled -> Color(0xFF0A0E13)
        filled -> Color(0xFF0A0E13).copy(alpha = 0.6f)
        enabled -> accent
        else -> Color.White.copy(alpha = 0.25f)
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(
                if (filled) Modifier
                else Modifier.border(2.dp, if (enabled) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f), CircleShape),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * if (filled) 0.48f else 0.6f),
        )
    }
}

@Composable
fun AppImage(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val source = app.icon
    if (source == null) {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.24f))
                .background(Color.White.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.label.take(1).uppercase(),
                color = CockpitColors.TextDim,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        return
    }
    val bitmap: ImageBitmap = remember(app.packageName) { source.asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = app.label,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun Artwork(
    media: MediaUiState,
    modifier: Modifier = Modifier,
    fallbackIcon: android.graphics.Bitmap? = null,
) {
    val artwork = media.artwork
    val artworkUri = media.artworkUri
    val appIcon = media.appIcon ?: fallbackIcon
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            artwork != null -> Image(
                bitmap = artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            !artworkUri.isNullOrBlank() -> AsyncImage(
                model = artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            appIcon != null -> Image(
                bitmap = appIcon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit,
            )

            else -> Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = CockpitColors.TextMuted,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@Composable
fun ContactAvatar(contact: ContactFav, size: Dp) {
    val background = remember(contact.name) { avatarColor(contact.name) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(contact.name),
            color = Color.White,
            fontSize = (size.value * 0.33f).sp,
            fontWeight = FontWeight.Bold,
        )
        if (!contact.photoUri.isNullOrBlank()) {
            AsyncImage(
                model = contact.photoUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    if (parts.size == 1) return parts[0].take(1).uppercase()
    return (parts[0].take(1) + parts[1].take(1)).uppercase()
}

private fun avatarColor(name: String): Color {
    val palette = listOf(
        Color(0xFF1E88E5),
        Color(0xFF00897B),
        Color(0xFF8E24AA),
        Color(0xFFD81B60),
        Color(0xFFF4511E),
        Color(0xFF546E7A),
        Color(0xFF43A047),
        Color(0xFF3949AB),
    )
    val index = (name.hashCode() and 0x7FFFFFFF) % palette.size
    return palette[index]
}

data class WeatherLook(val icon: ImageVector, val label: String, val color: Color)

fun weatherLook(code: Int): WeatherLook = when (code) {
    0 -> WeatherLook(Icons.Filled.WbSunny, "Dégagé", Color(0xFFFFC85C))
    1, 2 -> WeatherLook(Icons.Filled.WbSunny, "Peu nuageux", Color(0xFFFFD98A))
    3 -> WeatherLook(Icons.Filled.Cloud, "Couvert", Color(0xFFB0BEC5))
    45, 48 -> WeatherLook(Icons.Filled.Cloud, "Brouillard", Color(0xFF90A4AE))
    51, 53, 55, 56, 57 -> WeatherLook(Icons.Filled.Grain, "Bruine", Color(0xFF81D4FA))
    61, 63, 65, 66, 67 -> WeatherLook(Icons.Filled.Grain, "Pluie", Color(0xFF64B5F6))
    71, 73, 75, 77 -> WeatherLook(Icons.Filled.AcUnit, "Neige", Color(0xFFE1F5FE))
    80, 81, 82 -> WeatherLook(Icons.Filled.Grain, "Averses", Color(0xFF4FC3F7))
    85, 86 -> WeatherLook(Icons.Filled.AcUnit, "Averses de neige", Color(0xFFE1F5FE))
    95, 96, 99 -> WeatherLook(Icons.Filled.Thunderstorm, "Orage", Color(0xFFB39DDB))
    else -> WeatherLook(Icons.Filled.Cloud, "Météo", Color(0xFFB0BEC5))
}

fun batteryIcon(level: Int, charging: Boolean): ImageVector = when {
    charging -> Icons.Filled.BatteryChargingFull
    level >= 90 -> Icons.Filled.BatteryFull
    level >= 65 -> Icons.Filled.Battery5Bar
    level >= 40 -> Icons.Filled.Battery3Bar
    level >= 15 -> Icons.Filled.Battery1Bar
    else -> Icons.Filled.BatteryAlert
}
