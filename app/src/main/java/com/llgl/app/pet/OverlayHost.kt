package com.llgl.app.pet

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Lifecycle plumbing for a Compose view that lives in a Service window instead of an Activity.
 * Compose refuses to attach without a lifecycle owner and a saved-state registry owner, and it
 * pauses its frame clock while the lifecycle is below STARTED, which is how the overlay stops
 * animating when the screen is off. Main thread only.
 */
class OverlayHost : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    init {
        // Both calls are only allowed while the lifecycle is still INITIALIZED.
        controller.performAttach()
        controller.performRestore(null)
    }

    override val lifecycle: Lifecycle
        get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    fun start() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun pause() {
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
