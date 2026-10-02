package com.llgl.app.pet

/** Things the turtle can say. The overlay maps each one to a string resource. */
enum class Line {
    MORNING,
    LUNCH,
    EVENING,
    SLEEPY,
    HUNGRY,
    FULL,
    YUM,
    BORED,
    HAPPY,
    STARTLED,
    PETTED,
    LANDED,
    ZZZ,
    IDLE_1,
    IDLE_2,
    IDLE_3,
    IDLE_4,
    IDLE_5,
    ;

    companion object {
        val IDLE_LINES = listOf(IDLE_1, IDLE_2, IDLE_3, IDLE_4, IDLE_5)
    }
}
