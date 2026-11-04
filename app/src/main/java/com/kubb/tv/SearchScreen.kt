package com.kubb.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kubb.tv.data.Summary

/**
 * Typing goes through the system IME (Chinese titles need it). The site has no pinyin
 * search and no autocomplete, so the side panel offers trending and recent keywords instead.
 */
@Composable
internal fun SearchScreen(vm: AppViewModel, open: (Summary) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var hot by remember { mutableStateOf(emptyList<String>()) }
    val recent by vm.library.searches.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { hot = runCatching { vm.hotKeywords() }.getOrDefault(emptyList()) }
    fun search(keyword: String) {
        val k = keyword.trim().ifEmpty { return }
        query = k
        submitted = k
        vm.library.rememberSearch(k)
        keyboard?.hide()
    }
    Row(Modifier.fillMaxWidth().padding(start = SafeX)) {
        Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SearchField(query, { query = it }) { search(query) }
            if (hot.isNotEmpty()) Keywords("热门搜索", hot, ::search)
            if (recent.isNotEmpty()) {
                Keywords("最近搜索", recent, ::search)
                Chip("清空记录") { vm.library.clearSearches() }
            }
        }
        Box(Modifier.weight(1f)) {
            if (submitted.isEmpty()) Message("输入片名，按确认搜索")
            else PosterGrid(vm.searchFeed(submitted).also { f -> LaunchedEffect(f) { if (f.state.items.isEmpty()) f.loadNext() } }, open = open, columns = 5)
        }
    }
}

/**
 * Shows the keyword; 确认 opens a dialog whose field takes the IME. Focusing a text field
 * directly would pop the keyboard whenever the D-pad passes over it.
 */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun done(search: Boolean) {
        keyboard?.hide()
        editing = false
        if (search) onSearch()
    }
    Box(
        Modifier.fillMaxWidth().focusMemory("search-field").background(Panel, RoundedCornerShape(10.dp))
            .border(2.dp, if (focused) Accent else TextMuted.copy(alpha = .3f), RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null) { editing = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(value.ifEmpty { "搜索片名" }, color = if (value.isEmpty()) TextMuted else TextPrimary, fontSize = 20.sp, maxLines = 1)
    }
    if (editing) Dialog(onDismissRequest = { done(false) }) {
        val input = remember { FocusRequester() }
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 22.sp), cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { done(true) }),
            modifier = Modifier.fillMaxWidth().focusRequester(input).background(Panel, RoundedCornerShape(10.dp))
                .border(2.dp, Accent, RoundedCornerShape(10.dp)).padding(16.dp),
        )
        LaunchedEffect(Unit) {
            input.requestFocus()
            keyboard?.show()
        }
    }
}

@Composable
private fun Keywords(title: String, words: List<String>, onPick: (String) -> Unit) {
    Column {
        Text(title, color = TextMuted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in words) Chip(w, Modifier.focusMemory("$title:$w")) { onPick(w) }
        }
    }
}
