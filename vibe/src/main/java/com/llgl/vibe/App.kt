package com.llgl.vibe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.llgl.vibe.ui.ComposerScreen
import com.llgl.vibe.ui.LibraryScreen
import com.llgl.vibe.ui.PlayerScreen

sealed interface Screen {
    data object Library : Screen
    data object Player : Screen
    data object Composer : Screen
}

@Composable
fun VibeRoot(deps: Deps) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    when (screen) {
        Screen.Library -> LibraryScreen(
            deps = deps,
            onOpen = { song ->
                deps.session.open(song)
                screen = Screen.Player
            },
            onNowPlaying = { screen = Screen.Player },
            onComposer = {
                deps.session.pause()
                screen = Screen.Composer
            },
        )

        Screen.Player -> PlayerScreen(deps = deps, onBack = { screen = Screen.Library })

        Screen.Composer -> ComposerScreen(deps = deps, onBack = { screen = Screen.Library })
    }
}
