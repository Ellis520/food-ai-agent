package com.foodmoment.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 社区署名头像。
 *
 * 服务端只返回作者昵称、不返回头像（community_posts 表没有头像列），
 * 所以这里按昵称做确定性哈希生成色块 —— 同一个名字在任何设备上颜色都一致。
 */
@Composable
fun AuthorAvatar(
    name: String,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val color = remember(name) { avatarColorFor(name) }
    val initial = remember(name) { avatarInitialOf(name) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 取名字首字：中文取第一个字，英文转大写 */
fun avatarInitialOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    return trimmed.take(1).uppercase()
}

/** 用哈希决定色相，固定饱和度与明度，保证深色/浅色主题下都能看清白字 */
fun avatarColorFor(name: String): Color {
    val seed = name.trim().ifEmpty { "?" }
    val hue = (abs(seed.hashCode()) % 360).toFloat()
    return Color.hsv(hue, 0.46f, 0.72f)
}
