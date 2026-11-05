package com.foodmoment.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foodmoment.app.AppContainer
import com.foodmoment.app.net.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 图片三种真实状态：加载中不是失败，失败也不能一直转圈。 */
sealed interface CommunityImageState {
    data object Loading : CommunityImageState
    data class Loaded(val bitmap: Bitmap) : CommunityImageState
    data object Failed : CommunityImageState
}

/**
 * 社区图片：服务端要求会话鉴权且返回 no-store，
 * 因此先查本地磁盘缓存，未命中再带 cookie 拉取并落盘。
 */
suspend fun loadCommunityBitmap(container: AppContainer, postId: Long, position: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        val cache = container.repo.images.communityCacheFile(postId, position)
        if (cache.exists()) {
            return@withContext ImageLoader.loadFile(cache.absolutePath)
        }

        val bytes = container.repo.community.imageBytes(postId, position) ?: return@withContext null
        runCatching {
            cache.parentFile?.mkdirs()
            cache.writeBytes(bytes)
        }
        ImageLoader.decode(bytes, 1080)
    }

@Composable
fun CommunityImage(
    container: AppContainer,
    postId: Long,
    position: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null
) {
    val state by produceState<CommunityImageState>(
        CommunityImageState.Loading, postId, position
    ) {
        value = CommunityImageState.Loading
        val bitmap = loadCommunityBitmap(container, postId, position)
        value = if (bitmap != null) CommunityImageState.Loaded(bitmap) else CommunityImageState.Failed
    }

    val base = modifier
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

    Box(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        contentAlignment = Alignment.Center
    ) {
        when (val current = state) {
            is CommunityImageState.Loaded -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )

            CommunityImageState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )

            CommunityImageState.Failed -> Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = "图片加载失败",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** 全屏看图：黑底、点空白关闭、双指缩放、右上角关闭 */
@Composable
fun FullscreenCommunityImage(
    container: AppContainer,
    postId: Long,
    position: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val state by produceState<CommunityImageState>(
            CommunityImageState.Loading, postId, position
        ) {
            value = CommunityImageState.Loading
            val bitmap = loadCommunityBitmap(container, postId, position)
            value = if (bitmap != null) CommunityImageState.Loaded(bitmap) else CommunityImageState.Failed
        }

        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transformable = rememberTransformableState { zoomChange, panChange, _ ->
            val next = (scale * zoomChange).coerceIn(1f, 5f)
            scale = next
            offset = if (next <= 1f) Offset.Zero else offset + panChange
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            when (val current = state) {
                is CommunityImageState.Loaded -> Image(
                    bitmap = current.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(transformable)
                )

                CommunityImageState.Loading -> CircularProgressIndicator(color = Color.White)

                CommunityImageState.Failed -> Text(
                    "图片加载失败\n\n检查社区服务端是否可达，或点右上角关闭",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}

@Suppress("unused")
private fun unusedCounter() = mutableIntStateOf(0)
