package com.kubb.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.kubb.tv.data.CATEGORIES
import com.kubb.tv.data.Summary
import kotlinx.coroutines.delay

internal data class TabItem(val key: String, val label: String, val icon: ImageVector? = null)

internal val TABS = listOf(TabItem("search", "", Icons.Rounded.Search), TabItem("home", "首页")) +
    CATEGORIES.map { TabItem(it.id, it.name) } +
    listOf(TabItem("favorites", "", Icons.Rounded.Bookmarks), TabItem("history", "", Icons.Rounded.History))

/** A full-screen page pushed over the tabs. */
internal sealed interface Overlay {
    val key: String

    data class Detail(val item: Summary) : Overlay {
        override val key get() = "detail:${item.id}"
    }

    /** [episode] null plays where the viewer left off. */
    data class Player(val item: Summary, val episode: Int?) : Overlay {
        override val key get() = "player:${item.id}"
    }
}

@OptIn(ExperimentalFoundationApi::class) // LocalBringIntoViewSpec
@Composable
fun TvApp(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf("home") }
    val overlays = remember { mutableStateListOf<Overlay>() }
    val headerFocus = remember { TABS.associate { it.key to FocusRequester() } }
    var headerHasFocus by remember { mutableStateOf(false) }
    val states = rememberSaveableStateHolder()
    val memories = remember { mutableMapOf<String, FocusMemory>() }
    fun memory(key: String) = memories.getOrPut(key) { FocusMemory(mutableStateOf(null)) }

    val open: (Summary) -> Unit = { overlays += Overlay.Detail(it) }
    val play: (Summary, Int?) -> Unit = { item, episode -> overlays += Overlay.Player(item, episode) }

    LaunchedEffect(Unit) {
        withFrameNanos {}
        headerFocus.getValue(tab).requestFocus()
    }
    BackHandler(enabled = overlays.isNotEmpty() || !headerHasFocus || tab != "home") {
        when {
            overlays.isNotEmpty() -> {
                states.removeState(overlays.removeAt(overlays.lastIndex).key)
                memory(overlays.lastOrNull()?.key ?: tab).restoring = true
            }
            !headerHasFocus -> headerFocus.getValue(tab).requestFocus()
            else -> {
                tab = "home"
                headerFocus.getValue("home").requestFocus()
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides EdgeBringIntoViewSpec) {
            Box(Modifier.fillMaxSize().background(Bg)) {
                val top = overlays.lastOrNull()
                if (top == null) Column(Modifier.fillMaxSize()) {
                    Header(tab, headerFocus, onFocused = { tab = it }, Modifier.onFocusChanged { headerHasFocus = it.hasFocus })
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        states.SaveableStateProvider("tab:$tab") {
                            CompositionLocalProvider(LocalFocusMemory provides memory(tab)) {
                                when (tab) {
                                    "home" -> HomeScreen(vm, open, play)
                                    "search" -> SearchScreen(vm, open)
                                    "favorites" -> FavoritesScreen(vm, open)
                                    "history" -> HistoryScreen(vm, play)
                                    else -> CategoryScreen(vm, tab, open)
                                }
                            }
                        }
                    }
                } else states.SaveableStateProvider(top.key) {
                    CompositionLocalProvider(LocalFocusMemory provides memory(top.key)) {
                        when (top) {
                            is Overlay.Detail -> DetailScreen(vm, top.item, play)
                            is Overlay.Player -> PlayerScreen(vm, top.item, top.episode)
                        }
                    }
                }
            }
        }
    }
}

/** The top tab bar. Moving onto a tab switches to it after a short pause, so passing over tabs loads nothing. */
@Composable
private fun Header(selected: String, focus: Map<String, FocusRequester>, onFocused: (String) -> Unit, modifier: Modifier) {
    var focusedTab by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedTab) {
        val key = focusedTab ?: return@LaunchedEffect
        delay(250)
        onFocused(key)
    }
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = SafeX, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("酷播", color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(16.dp))
        for (t in TABS) {
            if (t.key == "favorites") Spacer(Modifier.width(24.dp))
            Chip(
                t.label, icon = t.icon, selected = t.key == selected,
                modifier = Modifier.focusRequester(focus.getValue(t.key))
                    .onFocusChanged { if (it.isFocused) focusedTab = t.key else if (focusedTab == t.key) focusedTab = null },
                onClick = { onFocused(t.key) },
            )
        }
    }
}
