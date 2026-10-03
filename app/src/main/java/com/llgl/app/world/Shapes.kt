package com.llgl.app.world

/** A round obstacle (a plant's base) that marbles and creatures go around. */
class Circle(val x: Float, val y: Float, val radius: Float)

/** The thumb on the glass: a soft disc that pushes sand and water aside and scares creatures. */
class Finger(var x: Float, var y: Float, val radius: Float) {
    var vx = 0f
    var vy = 0f
}

/** A crumb dropped by a long press; isopods come to nibble it until it is gone. */
class Crumb(val x: Float, val y: Float) {
    var amount = 1f
}

/** What happened this step that deserves a sound or a buzz. */
sealed interface Event {
    /** A marble (or curled isopod) hit a wall or another marble at [speed]; [radius] picks the pitch. */
    data class Impact(val x: Float, val y: Float, val speed: Float, val radius: Float, val wall: Boolean) : Event

    data class Tap(val x: Float, val y: Float, val onWater: Boolean) : Event

    data class Curl(val x: Float, val y: Float) : Event

    data class Shake(val strength: Float) : Event

    data object Chirp : Event
}
