package com.llgl.app.pet

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llgl.app.R

/** Pre-rendered sprite bitmaps at 1:1 sprite pixels; they are scaled up with nearest-neighbour when drawn. */
class SpriteSheet {
    private val cache = HashMap<Pair<PetSprites.Pose, Facing>, ImageBitmap>()

    val lettuce: ImageBitmap = toImage(PetSprites.LETTUCE.pixels(), PetSprites.LETTUCE.width, PetSprites.LETTUCE.height)

    fun frame(pose: PetSprites.Pose, facing: Facing): ImageBitmap = cache.getOrPut(pose to facing) {
        val frame = PetSprites.frame(pose)
        toImage(frame.pixels(mirrored = facing == Facing.LEFT), frame.width, frame.height)
    }

    private fun toImage(pixels: IntArray, width: Int, height: Int): ImageBitmap =
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

fun Line.resId(): Int = when (this) {
    Line.MORNING -> R.string.line_morning
    Line.LUNCH -> R.string.line_lunch
    Line.EVENING -> R.string.line_evening
    Line.SLEEPY -> R.string.line_sleepy
    Line.HUNGRY -> R.string.line_hungry
    Line.FULL -> R.string.line_full
    Line.YUM -> R.string.line_yum
    Line.BORED -> R.string.line_bored
    Line.HAPPY -> R.string.line_happy
    Line.STARTLED -> R.string.line_startled
    Line.PETTED -> R.string.line_petted
    Line.LANDED -> R.string.line_landed
    Line.ZZZ -> R.string.line_zzz
    Line.IDLE_1 -> R.string.line_idle_1
    Line.IDLE_2 -> R.string.line_idle_2
    Line.IDLE_3 -> R.string.line_idle_3
    Line.IDLE_4 -> R.string.line_idle_4
    Line.IDLE_5 -> R.string.line_idle_5
}

private val BubbleShape = RoundedCornerShape(6.dp)
private val Ink = Color(0xFF2B2B2B)

/** The contents of the overlay window: the turtle sprite at the bottom, a bubble or menu above it. */
@Composable
fun PetOverlay(window: OverlayWindow) {
    val snap = window.snapshot
    val sheet = remember { SpriteSheet() }
    val density = LocalDensity.current
    val spriteWDp = with(density) { window.spriteW.toDp() }
    val spriteHDp = with(density) { window.spriteH.toDp() }
    val lettuceDp = with(density) { window.lettuceSize.toDp() }

    LaunchedEffect(window) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                window.onFrame(dt)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        ) {
            when {
                snap.menuOpen -> MenuRow(window)
                snap.bubble != null -> Bubble(text = stringResource(snap.bubble.resId()))
            }
        }

        snap.lettuceOffsetX?.let { x ->
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(x, 0) }
                    .size(lettuceDp),
            ) {
                drawImage(
                    image = sheet.lettuce,
                    dstSize = IntSize(window.lettuceSize, window.lettuceSize),
                    filterQuality = FilterQuality.None,
                )
            }
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(snap.spriteOffsetX, 0) }
                .size(spriteWDp, spriteHDp)
                .pointerInput(window) {
                    detectTapGestures(onTap = { window.onTap() }, onLongPress = { window.onLongPress() })
                }
                .pointerInput(window) {
                    detectDragGestures(
                        onDragStart = { window.onDragStart() },
                        onDrag = { change, _ ->
                            change.consume()
                            window.onDragMove()
                        },
                        onDragEnd = { window.onDragEnd() },
                        onDragCancel = { window.onDragEnd() },
                    )
                },
        ) {
            drawImage(
                image = sheet.frame(snap.pose, snap.facing),
                dstOffset = IntOffset(0, -snap.bob * window.pixelScale),
                dstSize = IntSize(window.spriteW, window.spriteH),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

@Composable
private fun Bubble(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White, BubbleShape)
            .border(2.dp, Ink, BubbleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text = text, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MenuRow(window: OverlayWindow) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MenuButton(emoji = "🥬", label = stringResource(R.string.menu_feed), onClick = window::feed)
        MenuButton(emoji = "✋", label = stringResource(R.string.menu_pet), onClick = window::pet)
        MenuButton(emoji = "⚙️", label = stringResource(R.string.menu_settings), onClick = window::openSettings)
        MenuButton(emoji = "✕", label = stringResource(R.string.menu_close), onClick = window::closeMenu)
    }
}

@Composable
private fun MenuButton(emoji: String, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Ink, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 18.sp, color = Ink)
    }
}
