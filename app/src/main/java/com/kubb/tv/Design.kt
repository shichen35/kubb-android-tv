package com.kubb.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.kubb.tv.data.Summary
import com.kubb.tv.data.imageUrl
import java.util.Locale

internal val Bg = Color(0xFF0F1115)
internal val Panel = Color(0xFF1C2029)
internal val Accent = Color(0xFFFF7A1A)
internal val OnAccent = Color(0xFF14100C)
internal val TextPrimary = Color(0xFFF2F3F5)
internal val TextMuted = Color(0xFF9BA1AD)
internal val SafeX = 48.dp
internal const val POSTER_RATIO = 2f / 3f

/**
 * Which item to focus when a page comes back (after closing a detail page or the player).
 * Each page provides its own; a focusable registers its [key] when focused and, while
 * [restoring] is on, takes focus back when it is composed again.
 */
internal class FocusMemory(val last: MutableState<String?>) {
    var restoring by mutableStateOf(false)
}

internal val LocalFocusMemory = compositionLocalOf<FocusMemory?> { null }

internal fun Modifier.focusMemory(key: String): Modifier = composed {
    val memory = LocalFocusMemory.current ?: return@composed Modifier
    val requester = remember { FocusRequester() }
    LaunchedEffect(memory.restoring) {
        if (memory.restoring && memory.last.value == key) {
            runCatching { requester.requestFocus() }
            memory.restoring = false
        }
    }
    Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) memory.last.value = key }
}

/** Scroll only as far as needed to show the focused item, with no centering jumps. */
internal object EdgeBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < 0f -> offset
        offset + size > containerSize -> offset + size - containerSize
        else -> 0f
    }
}

internal fun scoreText(score: Double) = if (score > 0) String.format(Locale.ROOT, "%.1f", score) else ""

@Composable
internal fun Poster(pic: String, modifier: Modifier = Modifier, width: Int = 240, height: Int = 360) {
    var failed by remember(pic) { mutableStateOf(pic.isBlank()) }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Panel), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = imageUrl(pic, width, height), contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, onSuccess = { failed = false }, onError = { failed = true },
        )
        if (failed) Icon(Icons.Rounded.ImageNotSupported, null, Modifier.size(32.dp), tint = TextMuted)
    }
}

/** A focusable poster with title and a meta line; [progress] (0..1) draws a bar under the artwork. */
@Composable
internal fun PosterCard(
    item: Summary,
    modifier: Modifier = Modifier,
    focusKey: String = item.id,
    subtitle: String = listOf(item.year, item.area, item.remarks).filter { it.isNotBlank() }.joinToString(" · "),
    progress: Float? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier
            .focusMemory(focusKey)
            .border(3.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(11.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Poster(item.pic, Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO))
            val score = scoreText(item.score)
            if (score.isNotEmpty()) Text(
                score, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (progress != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = .6f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Accent))
            }
        }
        Text(item.name, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        Text(subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A pill button: filled accent when focused, outlined when [selected]. */
@Composable
internal fun Chip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fg = when {
        focused -> OnAccent
        selected -> Accent
        else -> TextPrimary
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Accent else Panel)
            .border(1.dp, if (selected && !focused) Accent else Color.Transparent, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = fg)
            if (label.isNotEmpty()) Spacer(Modifier.width(6.dp))
        }
        if (label.isNotEmpty()) Text(label, color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
internal fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp, 20.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
        Spacer(Modifier.width(10.dp))
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** A centered message with an optional action, for loading, empty and error states. */
@Composable
internal fun Message(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().then(modifier), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = TextMuted, fontSize = 18.sp)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Chip(action, onClick = onAction)
        }
    }
}
