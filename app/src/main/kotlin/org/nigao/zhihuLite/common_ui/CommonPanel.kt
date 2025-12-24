package org.nigao.zhihuLite.common_ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.nigao.zhihuLite.basicTypeExtension.noRippleClickable

data class PanelConfig(
    val backgroundColor: Color = Color.White,
    val contentHeightFraction: Float? = null,
    val cornerRadius: Dp = 16.dp,
    val dismissOnClickMask: Boolean = true,
    val animationDuration: Int = 300,
)

@Composable
fun CommonPanel(
    onDismiss: () -> Unit = {},
    config: PanelConfig = PanelConfig(),
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false) }

    LaunchedEffect(Unit) {
        visibleState.targetState = true
    }

    LaunchedEffect(visibleState.currentState) {
        if (visibleState.targetState == false && visibleState.isIdle) {
            onDismiss.invoke()
        }
    }

    if (visibleState.targetState || visibleState.currentState) {
        Dialog(
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            ),
            onDismissRequest = {
                visibleState.targetState = false
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = fadeIn(animationSpec = tween(config.animationDuration)),
                    exit = fadeOut(animationSpec = tween(config.animationDuration))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (config.dismissOnClickMask) {
                                    Modifier.noRippleClickable { visibleState.targetState = false }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = slideInVertically(
                        animationSpec = tween(config.animationDuration),
                        initialOffsetY = { it }
                    ),
                    exit = slideOutVertically(
                        animationSpec = tween(config.animationDuration),
                        targetOffsetY = { it }
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box (
                        modifier = Modifier.then(
                                if (config.contentHeightFraction != null) {
                                    Modifier
                                        .fillMaxHeight(config.contentHeightFraction)
                                } else {
                                    Modifier
                                }
                            )
                            .fillMaxWidth()
                            .background(
                                color = config.backgroundColor,
                                shape = RoundedCornerShape(topStart = config.cornerRadius, topEnd = config.cornerRadius)
                            )
                    ) {
                        content({
                            visibleState.targetState = false
                        })
                    }
                }
            }
        }
    }


}