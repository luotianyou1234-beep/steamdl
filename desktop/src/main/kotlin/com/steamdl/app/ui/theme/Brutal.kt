package com.steamdl.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 新粗野主义设计 token：与 steamdownload.cn 站点 `web/src/styles/theme.less` 同源。
 * 需要站点点缀色（按钮底色、tag、分区色）时从这里取，不要在页面里散落裸色值。
 */
object Brutal {
    val Ink = Color(0xFF141414)
    val Paper = Color(0xFFFFF8E7)
    val Paper2 = Color(0xFFF6ECD2)
    val Card = Color(0xFFFFFFFF)
    val Yellow = Color(0xFFFFD23F)
    val YellowSoft = Color(0xFFFFF1B8)
    val Pink = Color(0xFFFF8FAB)
    val PinkSoft = Color(0xFFFFE1E9)
    val Blue = Color(0xFF7CB7FF)
    val BlueSoft = Color(0xFFE1EFFF)
    val Green = Color(0xFF9BE15D)
    val GreenSoft = Color(0xFFE8F8D8)
    val Orange = Color(0xFFFFB347)
    val OrangeSoft = Color(0xFFFFECCF)
    val Purple = Color(0xFFB69CFF)
    val PurpleSoft = Color(0xFFECE5FF)
    val Red = Color(0xFFFF5C5C)
    val Text2 = Color(0xFF4A4538)
    val Text3 = Color(0xFF6B6552)
}

/** 站点圆角刻度：@r-sm 7 / @r 10 / 弹窗 14（对应 M3 small/medium/large） */
val BrutalShapes = Shapes(
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp)
)

/**
 * 站点 `.card()` 同款：白（或彩色）底 + ink 描边 + 右下硬偏移投影（无模糊）。
 *
 * 实现：先按完整范围画一块偏移的 ink 圆角矩形，再让背景/描边只画在
 * [0, w-off]x[0, h-off] 区域内 —— 露出来的右、下两条边就是硬阴影。
 */
fun Modifier.brutalCard(
    container: Color = Brutal.Card,
    cornerRadius: Dp = 10.dp,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 4.dp,
): Modifier = this
    .drawBehind {
        val off = shadowOffset.toPx()
        drawRoundRect(
            color = Brutal.Ink,
            topLeft = Offset(off, off),
            size = Size(size.width - off, size.height - off),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }
    .padding(end = shadowOffset, bottom = shadowOffset)
    .background(container, RoundedCornerShape(cornerRadius))
    .border(borderWidth, Brutal.Ink, RoundedCornerShape(cornerRadius))

/** 站点 `.btn()` 同款：彩色底 + ink 描边 + 3px 硬阴影 */
fun Modifier.brutalButton(
    container: Color = Brutal.Yellow,
    cornerRadius: Dp = 7.dp,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 3.dp,
): Modifier = brutalCard(container, cornerRadius, borderWidth, shadowOffset)
