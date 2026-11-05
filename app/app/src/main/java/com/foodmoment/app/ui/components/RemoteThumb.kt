package com.foodmoment.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foodmoment.app.net.ImageLoader

/**
 * 公开网络图片缩略图（不需要会话鉴权）。
 * 三种状态都要有出口：加载中、成功、失败，不能一直转圈。
 */
@Composable
fun RemoteThumb(
    url: String?,
    size: Dp = 56.dp,
    corner: Int = 10
) {
    val state by produceState<Result<android.graphics.Bitmap?>?>(initialValue = null, url) {
        value = if (url.isNullOrBlank()) Result.success(null) else runCatching { ImageLoader.load(url) }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            state == null -> Unit

            state?.getOrNull() != null -> Image(
                bitmap = state!!.getOrNull()!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            else -> Icon(
                Icons.Filled.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2.5f)
            )
        }
    }
}

/** 明确的失败占位，用于需要区分「没有图」和「图挂了」的地方 */
@Composable
fun BrokenThumb(size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.BrokenImage,
            contentDescription = "图片加载失败",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2.5f)
        )
    }
}
