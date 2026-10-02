package com.llgl.app.pet

/**
 * Pixel-art frames for the turtle, drawn as character grids so they live in code and can be
 * checked by unit tests. Every turtle frame is [WIDTH] x [HEIGHT] and faces right; the renderer
 * mirrors it for the other direction.
 */
object PetSprites {
    const val WIDTH = 24
    const val HEIGHT = 16
    const val LETTUCE_SIZE = 8

    /** Character -> ARGB. '.' is transparent. */
    val PALETTE: Map<Char, Int> = mapOf(
        '.' to 0x00000000,
        'g' to 0xFF3FA34D.toInt(), // shell
        'G' to 0xFF2B7A3A.toInt(), // shell dark
        'h' to 0xFF72D672.toInt(), // shell highlight
        'y' to 0xFFB9E66E.toInt(), // skin
        'Y' to 0xFF8CC04B.toInt(), // skin dark
        'e' to 0xFF1E1E1E.toInt(), // eye
        'w' to 0xFFFFFFFF.toInt(), // white
        'l' to 0xFF7EE05C.toInt(), // lettuce
        'L' to 0xFF4CAF50.toInt(), // lettuce dark
        'z' to 0xFFE8F0FF.toInt(), // sleep "z"
    )

    class Frame(val rows: List<String>) {
        val width: Int = rows.first().length
        val height: Int = rows.size

        /** Row-major ARGB pixels, optionally mirrored left-to-right. */
        fun pixels(mirrored: Boolean = false): IntArray {
            val out = IntArray(width * height)
            for ((r, row) in rows.withIndex()) {
                for (c in 0 until width) {
                    val ch = row[if (mirrored) width - 1 - c else c]
                    out[r * width + c] = PALETTE[ch] ?: 0
                }
            }
            return out
        }
    }

    enum class Pose { WALK_A, WALK_B, BLINK, HIDE_A, HIDE_B, SLEEP_A, SLEEP_B, EAT_A, EAT_B, EAT_C, CARRIED_A, CARRIED_B }

    /** Which frame to show and how many sprite pixels to lift it (walking bob). */
    data class Cel(val pose: Pose, val bob: Int)

    private const val EMPTY = "........................"

    val WALK_A = Frame(
        listOf(
            EMPTY,
            "........hhhhhhh.........",
            "......hhgggggggghh......",
            ".....hgggGgggGggggh.....",
            "....hggGgggggggGgggh....",
            "....gggggGgggGgggggg....",
            "...GgggGgggggggggGggG.yy",
            "...GggggggGgggGggggGyyey",
            "....GGGGGGGGGGGGGGGGyyyy",
            "..YY.YYYY......YYYY.yyYY",
            "...Y.YYYY......YYYY..YY.",
            ".....YYY........YYY.....",
            EMPTY,
            EMPTY,
            EMPTY,
            EMPTY,
        ),
    )

    val WALK_B = Frame(
        WALK_A.rows.toMutableList().apply {
            this[9] = "..YY..YYYY....YYYY..yyYY"
            this[10] = "...Y..YYYY....YYYY...YY."
            this[11] = "......YYY......YYY......"
        },
    )

    val BLINK = Frame(
        WALK_A.rows.toMutableList().apply {
            this[7] = "...GggggggGgggGggggGyyYy"
        },
    )

    val HIDE_A = Frame(
        listOf(
            EMPTY,
            EMPTY,
            EMPTY,
            "........hhhhhhh.........",
            "......hhgggggggghh......",
            ".....hgggGgggGggggh.....",
            "....hggGgggggggGgggh....",
            "....gggggGgggGgggggg....",
            "...GgggGgggggggggGggG...",
            "...GggggggGgggGggggG....",
            "....GGGGGGGGGGGGGGGG....",
            EMPTY,
            EMPTY,
            EMPTY,
            EMPTY,
            EMPTY,
        ),
    )

    val HIDE_B = Frame(
        HIDE_A.rows.toMutableList().apply {
            this[9] = "...GggggggGgggGggggGye.."
            this[10] = "....GGGGGGGGGGGGGGGGyy.."
        },
    )

    val SLEEP_A = Frame(
        BLINK.rows.toMutableList().apply {
            this[2] = "......hhgggggggghh....z."
            this[4] = "....hggGgggggggGgggh.z.."
        },
    )

    val SLEEP_B = Frame(
        BLINK.rows.toMutableList().apply {
            this[1] = "........hhhhhhh.......z."
            this[3] = ".....hgggGgggGggggh..z.."
        },
    )

    val EAT_A = Frame(
        WALK_A.rows.toMutableList().apply {
            this[6] = "...GgggGgggggggggGggG..."
            this[7] = "...GggggggGgggGggggG.yy."
            this[8] = "....GGGGGGGGGGGGGGGGyyey"
            this[9] = "..YY.YYYY......YYYY.yyyy"
            this[10] = "...Y.YYYY......YYYY.yyYY"
            this[11] = ".....YYY........YYY..YY."
        },
    )

    val EAT_B = Frame(
        EAT_A.rows.toMutableList().apply {
            this[7] = "...GggggggGgggGggggG...."
            this[8] = "....GGGGGGGGGGGGGGGGyyy."
            this[9] = "..YY.YYYY......YYYY.yyey"
            this[10] = "...Y.YYYY......YYYY.yyyy"
            this[11] = ".....YYY........YYY.YYY."
        },
    )

    val EAT_C = Frame(
        EAT_A.rows.toMutableList().apply {
            this[8] = "....GGGGGGGGGGGGGGGGyyYy"
        },
    )

    val CARRIED_A = Frame(
        WALK_A.rows.toMutableList().apply {
            this[9] = "...YY.YYY.......YYY.yyYY"
            this[10] = "....Y.YYY.......YYY..YY."
            this[11] = "......YY.........YY....."
            this[12] = "......YY.........YY....."
        },
    )

    val CARRIED_B = Frame(
        WALK_A.rows.toMutableList().apply {
            this[9] = "..YY..YYY.....YYY...yyYY"
            this[10] = "...Y...YYY...YYY.....YY."
            this[11] = ".......YY...YY.........."
        },
    )

    val LETTUCE = Frame(
        listOf(
            "..lLll..",
            ".lLllLl.",
            "lLllLllL",
            "lllLllll",
            "Lll.llLl",
            ".lLlLll.",
            "..lLll..",
            "........",
        ),
    )

    fun frame(pose: Pose): Frame = when (pose) {
        Pose.WALK_A -> WALK_A
        Pose.WALK_B -> WALK_B
        Pose.BLINK -> BLINK
        Pose.HIDE_A -> HIDE_A
        Pose.HIDE_B -> HIDE_B
        Pose.SLEEP_A -> SLEEP_A
        Pose.SLEEP_B -> SLEEP_B
        Pose.EAT_A -> EAT_A
        Pose.EAT_B -> EAT_B
        Pose.EAT_C -> EAT_C
        Pose.CARRIED_A -> CARRIED_A
        Pose.CARRIED_B -> CARRIED_B
    }

    /** Picks the frame for [activity] at animation time [t] (seconds). */
    fun celFor(activity: Activity, t: Float): Cel = when (activity) {
        Activity.WALK -> {
            val step = (t * 5f).toInt() % 2
            Cel(if (step == 0) Pose.WALK_A else Pose.WALK_B, bob = step)
        }
        Activity.IDLE -> Cel(if (t % 3.2f > 3.0f) Pose.BLINK else Pose.WALK_A, 0)
        Activity.HIDE -> Cel(if (t % 1.6f < 1.0f) Pose.HIDE_A else Pose.HIDE_B, 0)
        Activity.SLEEP -> Cel(if (t % 1.6f < 0.8f) Pose.SLEEP_A else Pose.SLEEP_B, 0)
        Activity.EAT -> Cel(listOf(Pose.EAT_A, Pose.EAT_B, Pose.EAT_C)[(t * 4f).toInt() % 3], 0)
        Activity.CARRIED -> Cel(if ((t * 6f).toInt() % 2 == 0) Pose.CARRIED_A else Pose.CARRIED_B, 0)
        Activity.FALL -> Cel(Pose.CARRIED_A, 0)
    }
}
