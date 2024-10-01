package org.futo.inputmethod.latin.uix.resizing

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.uix.safeKeyboardPadding
import org.futo.inputmethod.v2keyboard.ComputedKeyboardSize
import org.futo.inputmethod.v2keyboard.FloatingKeyboardSize
import org.futo.inputmethod.v2keyboard.OneHandedDirection
import org.futo.inputmethod.v2keyboard.OneHandedKeyboardSize
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import org.futo.inputmethod.v2keyboard.SplitKeyboardSize
import org.futo.inputmethod.v2keyboard.getHeight
import kotlin.math.roundToInt

class KeyboardResizers(val latinIME: LatinIME) {
    private val resizing = mutableStateOf(false)

    @Composable
    private fun BoxScope.FloatingKeyboardResizer(size: FloatingKeyboardSize) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            // Matching the necessary coordinate space
            var deltaX = delta.left
            var deltaY = -delta.bottom
            var deltaWidth = delta.right - delta.left
            var deltaHeight = delta.bottom - delta.top

            var result = true

            // TODO: Limit the values so that we do not go off-screen
            // If we have reached a minimum limit, return false

            // Basic limiting for minimum size
            val currSettings = latinIME.sizingCalculator.getSavedSettings()
            val currSize = Size(
                currSettings.floatingWidthDp.dp.toPx(),
                currSettings.floatingHeightDp.dp.toPx()
            )

            if(currSize.width + deltaWidth < 200.dp.toPx()) {
                deltaWidth = deltaWidth.coerceAtLeast(200.dp.toPx() - currSize.width)
                deltaX = 0.0f
                result = false
            }

            if(currSize.height + deltaHeight < 160.dp.toPx()) {
                deltaHeight = deltaHeight.coerceAtLeast(160.dp.toPx() - currSize.height)
                deltaY = 0.0f
                result = false
            }

            latinIME.sizingCalculator.editSavedSettings { settings ->
                settings.copy(
                    floatingBottomOriginDp = Pair(
                        settings.floatingBottomOriginDp.first + deltaX.toDp().value,
                        settings.floatingBottomOriginDp.second + deltaY.toDp().value
                    ),
                    floatingWidthDp = settings.floatingWidthDp + deltaWidth.toDp().value,
                    floatingHeightDp = settings.floatingHeightDp + deltaHeight.toDp().value
                )
            }

            result
        }, true, {
            resizing.value = false
        }, {
            // Reset
        })
    }

    @Composable
    private fun BoxScope.RegularKeyboardResizer(size: RegularKeyboardSize) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            val sideDelta = delta.left - delta.right
            latinIME.sizingCalculator.editSavedSettings { settings ->
                val existingHeight = latinIME.size.value!!.getHeight()
                var targetHeight = existingHeight + delta.top

                var newSidePadding =
                    (settings.paddingDp.left + sideDelta.toDp().value)
                if (newSidePadding !in 0.0f..64.0f) {
                    newSidePadding = newSidePadding.coerceIn(0.0f..64.0f)
                    result = false
                }

                var newBottomPadding =
                    (settings.paddingDp.bottom - delta.bottom.toDp().value)
                if (newBottomPadding !in 0.0f..64.0f) {
                    // Correct for height difference if it's being dragged up/down
                    val correction = if (newBottomPadding < 0.0f) {
                        newBottomPadding.dp.toPx().coerceAtLeast(-delta.top)
                    } else {
                        (newBottomPadding - 64.0f).dp.toPx()
                            .coerceAtMost(-delta.top)
                    }
                    targetHeight += correction

                    newBottomPadding = newBottomPadding.coerceIn(0.0f..64.0f)
                    result = false
                }

                var newHeightMultiplier =
                    (settings.heightMultiplier * (existingHeight / targetHeight))
                if (newHeightMultiplier !in 0.3f..2.0f) {
                    newHeightMultiplier =
                        newHeightMultiplier.coerceIn(0.3f..2.0f)
                    result = false
                }

                settings.copy(
                    paddingDp = Rect(
                        newSidePadding.roundToInt(),
                        settings.paddingDp.top,
                        newSidePadding.roundToInt(),
                        newBottomPadding.roundToInt(),
                    ),
                    heightMultiplier = newHeightMultiplier
                )
            }
            result
        }, true, {
            resizing.value = false
        }, {
            // TODO: Reset
        })
    }

    @Composable
    private fun BoxScope.OneHandedResizer(size: OneHandedKeyboardSize) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            latinIME.sizingCalculator.editSavedSettings { settings ->
                val existingHeight = latinIME.size.value!!.getHeight()
                var targetHeight = existingHeight + delta.top

                // These have to be flipped in right handed mode for the setting
                var deltaLeft = if(size.direction == OneHandedDirection.Left) {
                    delta.left
                } else {
                    -delta.right
                }

                var deltaRight = if(size.direction == OneHandedDirection.Left) {
                    delta.right
                } else {
                    -delta.left
                }

                var newLeft = settings.oneHandedRectDp.left + deltaLeft.toDp().value
                if(newLeft < 0) {
                    // prevent shrinking when being dragged into the wall
                    if(deltaRight < 0.0f) {
                        deltaRight -= newLeft.dp.toPx()
                    }
                    newLeft = 0.0f

                    result = false
                }

                var newRight = settings.oneHandedRectDp.right + deltaRight.toDp().value
                if(newRight < 0) {
                    // this should never happen, but just in case
                    newRight = 0.0f
                    result = false
                }

                // prevent being dragged to the wrong side of the keyboard
                val newCenter = (newLeft + newRight) / 2.0f
                val limit = (latinIME.getViewWidth().toDp().value) / 2.0f
                if(newCenter > limit) {
                    val diff = newCenter - limit
                    newLeft -= diff
                    newRight -= diff
                    result = false
                }

                var newBottomPadding = (settings.oneHandedRectDp.bottom - delta.bottom.toDp().value)
                if (newBottomPadding !in 0.0f..80.0f) {
                    // Correct for height difference if it's being dragged up/down
                    val correction = if (newBottomPadding < 0.0f) {
                        newBottomPadding.dp.toPx().coerceAtLeast(-delta.top)
                    } else {
                        (newBottomPadding - 80.0f).dp.toPx()
                            .coerceAtMost(-delta.top)
                    }
                    targetHeight += correction

                    newBottomPadding = newBottomPadding.coerceIn(0.0f..80.0f)
                    result = false
                }

                var newHeightMultiplier =
                    ((settings.oneHandedHeightMultiplier ?: settings.heightMultiplier) * (existingHeight / targetHeight))
                if (newHeightMultiplier !in 0.3f..2.0f) {
                    newHeightMultiplier =
                        newHeightMultiplier.coerceIn(0.3f..2.0f)
                    result = false
                }

                settings.copy(
                    oneHandedRectDp = Rect(
                        newLeft.toInt(),
                        settings.oneHandedRectDp.top,
                        newRight.toInt(),
                        newBottomPadding.toInt(),
                    ),
                    oneHandedHeightMultiplier = newHeightMultiplier
                )
            }
            result
        }, true, {
            resizing.value = false
        }, {
            // TODO: Reset
        })
    }

    @Composable
    private fun BoxScope.SplitKeyboardResizer(size: SplitKeyboardSize) = with(LocalDensity.current) {
        Box(
            modifier = Modifier.matchParentSize()
                .width(size.splitLayoutWidth.toDp()).align(
                    Alignment.CenterStart
                )
        ) {
            ResizerRect({ delta ->
                true
            }, true, {
                resizing.value = false
            }, {

            })
        }
    }



    @Composable
    fun Resizer(boxScope: BoxScope, size: ComputedKeyboardSize) = with(boxScope) {
        if(!resizing.value) return

        Box(Modifier.matchParentSize().safeKeyboardPadding()) {
            when (size) {
                is OneHandedKeyboardSize -> OneHandedResizer(size)
                is RegularKeyboardSize -> RegularKeyboardResizer(size)
                is SplitKeyboardSize -> SplitKeyboardResizer(size)
                is FloatingKeyboardSize -> FloatingKeyboardResizer(size)
            }
        }
    }

    fun displayResizer() {
        resizing.value = true
    }

    fun hideResizer() {
        resizing.value = false
    }
}