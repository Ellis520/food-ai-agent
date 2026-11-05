package com.foodmoment.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 极简图表：Compose Canvas 自绘，无第三方依赖（8.1）。
 * 没有数据的日期不画成 0，而是留白（HAB-06）。
 */
@Composable
fun BarChart(
    points: List<Pair<String, Double>>,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (points.isEmpty()) {
        EmptyState("暂无数据")
        return
    }
    val max = points.maxOfOrNull { it.second } ?: 0.0
    val safeMax = if (max <= 0.0) 1.0 else max

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val count = points.size
            if (count == 0) return@Canvas
            val slot = size.width / count
            val barWidth = slot * 0.55f
            points.forEachIndexed { index, pair ->
                val ratio = (pair.second / safeMax).toFloat().coerceIn(0f, 1f)
                val barHeight = size.height * ratio
                val left = slot * index + (slot - barWidth) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { pair ->
                Text(
                    text = pair.first,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = "单位：$unit · 峰值 ${format1(max)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun LineChart(
    points: List<Pair<String, Double?>>,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val values = points.mapNotNull { it.second }
    if (values.isEmpty()) {
        EmptyState("暂无数据", "有记录的日期才会绘制")
        return
    }
    val max = values.maxOrNull() ?: 1.0
    val safeMax = if (max <= 0.0) 1.0 else max

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val count = values.size
            if (count < 2) {
                drawCircle(color, radius = 6f, center = Offset(size.width / 2f, size.height / 2f))
                return@Canvas
            }
            val stepX = size.width / (count - 1)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = stepX * index
                val y = size.height - (value / safeMax).toFloat() * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 4f))
            values.forEachIndexed { index, value ->
                val x = stepX * index
                val y = size.height - (value / safeMax).toFloat() * size.height
                drawCircle(color, radius = 5f, center = Offset(x, y))
            }
        }
        Text(
            text = "单位：$unit · 最高 ${format1(max)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun format1(value: Double): String =
    if (value >= 100) value.toInt().toString() else String.format(java.util.Locale.CHINA, "%.1f", value)

@Composable
fun LegendRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
