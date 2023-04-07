@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package dev.fabik.bluetoothhid.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.fabik.bluetoothhid.ui.theme.Typography

@Composable
fun Tooltip(
    expanded: MutableState<Boolean>,
    content: @Composable () -> Unit
) {
    val expandedStates = remember { MutableTransitionState(false) }

    LaunchedEffect(expanded.value) {
        expandedStates.targetState = expanded.value
    }

    if (expandedStates.currentState || expandedStates.targetState) {
        val density = LocalDensity.current

        Popup(
            onDismissRequest = { expanded.value = false },
            popupPositionProvider = DropdownMenuPositionProvider(DpOffset.Zero, density),
            properties = PopupProperties(focusable = true),
        ) {
            TooltipContent(
                expandedStates,
                content
            )
        }
    }
}

@Composable
fun TooltipContent(
    expandedStates: MutableTransitionState<Boolean>,
    content: @Composable () -> Unit
) {
    val transition = updateTransition(expandedStates, "Tooltip")

    val alpha by transition.animateFloat(
        transitionSpec = {
            if (true isTransitioningTo false) {
                tween(durationMillis = 300, delayMillis = 800)
            } else {
                tween(durationMillis = 200)
            }
        },
        label = "Tooltip alpha"
    ) { expanded ->
        if (expanded) 1f else 0f
    }

    Card(
        modifier = Modifier
            .alpha(alpha)
            .padding(4.dp),
        shape = MaterialTheme.shapes.extraSmall,
        elevation = CardDefaults.elevatedCardElevation(4.dp),
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            content()
        }
    }
}

fun Modifier.tooltip(text: String) = composed {
    val showTooltip = remember { mutableStateOf(false) }

    Tooltip(showTooltip) {
        Text(text, style = Typography.bodyLarge)
    }

    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)

            // Check if not cancelled
            if (longPress != null) {
                showTooltip.value = true

                // Wait for up event and consume it
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Release) {
                        event.changes.forEach { it.consume() }
                        showTooltip.value = false // Hide animation has 800ms delay
                        break
                    }
                }
            }
        }
    }
}
