package com.llgl.gameforge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llgl.gameforge.gen.GenerationController
import com.llgl.gameforge.ui.CodeScreen
import com.llgl.gameforge.ui.GenerateScreen
import com.llgl.gameforge.ui.LibraryScreen
import com.llgl.gameforge.ui.ModelScreen
import com.llgl.gameforge.ui.PlayScreen

sealed interface Screen {
    data object Library : Screen
    data object Model : Screen
    data class Generate(val returnTo: Screen) : Screen
    data class Play(val gameId: String) : Screen
    data class Code(val gameId: String) : Screen
}

/** The whole app: five screens and a hand-rolled back stack of depth one. */
@Composable
fun GameForgeRoot(deps: Deps) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    var libraryVersion by remember { mutableIntStateOf(0) }
    val outcome by deps.generation.outcome.collectAsStateWithLifecycle()

    // A finished generation opens its game, wherever we are.
    LaunchedEffect(outcome) {
        val saved = outcome as? GenerationController.Outcome.Saved ?: return@LaunchedEffect
        libraryVersion++
        screen = Screen.Play(saved.gameId)
        deps.generation.clearOutcome()
    }

    when (val s = screen) {
        Screen.Library -> LibraryScreen(
            deps = deps,
            refresh = libraryVersion,
            onCreate = { idea ->
                deps.generation.start(GenerationController.Job.Create(idea))
                screen = Screen.Generate(Screen.Library)
            },
            onOpen = { screen = Screen.Play(it) },
            onModel = { screen = Screen.Model },
            onDelete = {
                deps.store.delete(it)
                libraryVersion++
            },
        )

        Screen.Model -> ModelScreen(deps = deps, onBack = { screen = Screen.Library })

        is Screen.Generate -> GenerateScreen(
            generation = deps.generation,
            onLeave = {
                libraryVersion++
                screen = s.returnTo
            },
        )

        is Screen.Play -> PlayScreen(
            deps = deps,
            gameId = s.gameId,
            onBack = {
                libraryVersion++
                screen = Screen.Library
            },
            onRevise = { request ->
                deps.generation.start(GenerationController.Job.Revise(s.gameId, request))
                screen = Screen.Generate(s)
            },
            onFix = { errors ->
                deps.generation.start(GenerationController.Job.Fix(s.gameId, errors))
                screen = Screen.Generate(s)
            },
            onCode = { screen = Screen.Code(s.gameId) },
            onDeleted = {
                libraryVersion++
                screen = Screen.Library
            },
        )

        is Screen.Code -> CodeScreen(deps = deps, gameId = s.gameId, onBack = { screen = Screen.Play(s.gameId) })
    }
}
