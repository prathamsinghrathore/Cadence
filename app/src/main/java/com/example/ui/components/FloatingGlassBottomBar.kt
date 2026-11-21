package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.BottomNavTab
import com.example.ui.theme.LocalAmbientPalette
import dev.chrisbanes.haze.HazeState

private val BottomBarPillShape = RoundedCornerShape(50)

private val LiquidBubbleSpring = spring<Float>(
    dampingRatio = 0.78f,
    stiffness = Spring.StiffnessMediumLow
)

@Composable
fun FloatingGlassBottomBar(
    selectedTab: BottomNavTab,
    hazeState: HazeState,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val tabs = BottomNavTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    // Smoothly blend the sliding convex Liquid Glass droplet tint with the active song's Palette
    val bubbleAccentTop by animateColorAsState(
        targetValue = ambient.lightVibrantColor,
        animationSpec = tween(durationMillis = 550),
        label = "navBubbleAccentTop"
    )
    val bubbleAccentBottom by animateColorAsState(
        targetValue = ambient.primaryGlow,
        animationSpec = tween(durationMillis = 550),
        label = "navBubbleAccentBottom"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer floating Apple iOS Liquid Glass pill (5 optical glass layers + 36.dp vibrant Haze blur)
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .height(64.dp)
                .liquidGlass(
                    hazeState = hazeState,
                    shape = BottomBarPillShape,
                    cornerRadiusHint = 50.dp
                )
        ) {
            val tabWidth = maxWidth / tabs.size
            val targetOffsetPx = with(density) { (tabWidth * selectedIndex).toPx() }
            val indicatorOffsetPx by animateFloatAsState(
                targetValue = targetOffsetPx,
                animationSpec = LiquidBubbleSpring,
                label = "navLiquidBubbleOffset"
            )

            // Layer 1: Single shared sliding convex Liquid Glass droplet indicator behind the tabs
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = indicatorOffsetPx
                    }
                    .padding(4.dp)
                    .graphicsLayer {
                        clip = true
                        shape = BottomBarPillShape
                    }
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val radius = minOf(w, h) / 2f
                        val cornerObj = CornerRadius(radius, radius)

                        // Translucent vibrant droplet body
                        val dropletFillBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                bubbleAccentTop.copy(alpha = 0.28f),
                                bubbleAccentBottom.copy(alpha = 0.22f)
                            ),
                            startY = 0f,
                            endY = h
                        )

                        // Convex top specular meniscus light-catch
                        val specularHeight = h * 0.54f
                        val topDomePath = Path().apply {
                            addOval(
                                Rect(
                                    left = -w * 0.10f,
                                    top = -specularHeight * 0.45f,
                                    right = w * 1.10f,
                                    bottom = specularHeight
                                )
                            )
                        }
                        val topDomeBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = specularHeight
                        )

                        // Soft lower internal caustic reflection
                        val causticBrush = Brush.radialGradient(
                            colors = listOf(
                                bubbleAccentTop.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.5f, h * 0.96f),
                            radius = maxOf(w * 0.48f, h * 0.7f)
                        )

                        // Subtle inner depth shadow inside droplet bevel
                        val innerShadowWidth = 1.dp.toPx()
                        val innerInset = 1.25.dp.toPx()
                        val innerShadowSize = Size(
                            (w - innerInset * 2f).coerceAtLeast(0f),
                            (h - innerInset * 2f).coerceAtLeast(0f)
                        )
                        val innerShadowCorner = CornerRadius(
                            (radius - innerInset).coerceAtLeast(0f),
                            (radius - innerInset).coerceAtLeast(0f)
                        )
                        val innerShadowStroke = Stroke(width = innerShadowWidth)

                        // Crisp chromatic / specular diagonal droplet rim
                        val rimWidth = 1.dp.toPx()
                        val rimInset = rimWidth / 2f
                        val rimSize = Size(
                            (w - rimWidth).coerceAtLeast(0f),
                            (h - rimWidth).coerceAtLeast(0f)
                        )
                        val rimCorner = CornerRadius(
                            (radius - rimInset).coerceAtLeast(0f),
                            (radius - rimInset).coerceAtLeast(0f)
                        )
                        val rimStroke = Stroke(width = rimWidth)
                        val dropletRimBrush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.62f),
                                Color.White.copy(alpha = 0.12f),
                                bubbleAccentTop.copy(alpha = 0.42f),
                                Color.White.copy(alpha = 0.38f)
                            ),
                            start = Offset.Zero,
                            end = Offset(w, h)
                        )

                        onDrawBehind {
                            drawRoundRect(
                                brush = dropletFillBrush,
                                cornerRadius = cornerObj
                            )
                            drawRoundRect(
                                brush = causticBrush,
                                cornerRadius = cornerObj
                            )
                            drawPath(
                                path = topDomePath,
                                brush = topDomeBrush
                            )
                            if (innerShadowSize.width > 0f && innerShadowSize.height > 0f) {
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = 0.18f),
                                    topLeft = Offset(innerInset, innerInset),
                                    size = innerShadowSize,
                                    cornerRadius = innerShadowCorner,
                                    style = innerShadowStroke
                                )
                            }
                            if (rimSize.width > 0f && rimSize.height > 0f) {
                                drawRoundRect(
                                    brush = dropletRimBrush,
                                    topLeft = Offset(rimInset, rimInset),
                                    size = rimSize,
                                    cornerRadius = rimCorner,
                                    style = rimStroke
                                )
                            }
                        }
                    }
                    .testTag("nav_sliding_bubble")
            )

            // Layer 2: Equal-width interactive tab slots with synchronized icon scale & color morph
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.58f),
                        animationSpec = tween(durationMillis = 220),
                        label = "navTabContentColor"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.12f else 1.0f,
                        animationSpec = LiquidBubbleSpring,
                        label = "navTabIconScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .minimumInteractiveComponentSize()
                            .clip(BottomBarPillShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTabSelected(tab)
                                }
                            )
                            .testTag(tab.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.labelRes),
                                tint = contentColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
