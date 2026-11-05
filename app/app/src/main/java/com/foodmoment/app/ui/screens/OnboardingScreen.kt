package com.foodmoment.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodmoment.app.core.Settings

/**
 * 首次引导（4.8 / ONB-01~04）。
 * 三步：风险提示 → 模型说明 → 免费额度与本地部署指引。全程可跳过。
 */
@Composable
fun OnboardingScreen(settings: Settings, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        LinearProgressIndicator(
            progress = { (step + 1) / 3f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> RiskStep()
            1 -> ModelStep()
            else -> GuideStep()
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                settings.onboarded = true
                onDone()
            }) { Text("跳过") }

            Button(onClick = {
                if (step < 2) step++ else {
                    settings.onboarded = true
                    onDone()
                }
            }) { Text(if (step < 2) "下一步" else "开始使用") }
        }
    }
}

@Composable
private fun RiskStep() {
    Text("使用前请了解", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(16.dp))
    listOf(
        "本应用不提供账号体系，所有数据保存在本机；卸载或换机前请自行导出。",
        "你填写的 API Key 会加密保存在本机，仅用于直连你指定的服务地址。请勿在公共设备上保存。",
        "对话内容会发送到你配置的模型服务，请勿输入身份证号、住址等敏感信息。",
        "若你启用社区并配置了服务端，你发布的帖子与图片会上传到该服务端，可见范围取决于该服务端的部署方式。社区默认关闭。"
    ).forEachIndexed { index, line ->
        Text("${index + 1}. $line", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ModelStep() {
    Text("AI 能力需要你自己配置模型", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(16.dp))
    Text(
        "本应用不提供模型服务，也不会替你付费。你需要在「设置 → 模型」填写三项：",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(12.dp))
    listOf(
        "Base URL：接口根地址，例如 https://api.deepseek.com/v1",
        "API Key：你从服务商获取的密钥",
        "模型名：例如 deepseek-chat"
    ).forEach {
        Text("· $it", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "费用由你选择的服务方结算。配置之前，记录、营养换算、习惯统计、离线模板计划都可以正常使用。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GuideStep() {
    Text("从哪里获取模型", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(16.dp))
    Text(
        "三条路径，任选其一：",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(12.dp))
    Text("1. 云服务商的免费额度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    Text(
        "多数厂商为新用户提供试用额度，额度与有效期以各家控制台为准，本应用不写死任何数字。到控制台确认「是否支持 OpenAI 兼容接口」和「剩余额度」即可。",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(12.dp))
    Text("2. 本地 Ollama（完全离线，零费用）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    Text(
        "在电脑上安装 Ollama 并拉取模型后，Base URL 填电脑的局域网地址，例如 http://192.168.1.20:11434/v1。\n\n" +
            "注意：手机上的 127.0.0.1 指向手机自身，填它一定连不上；同时需要让 Ollama 监听 0.0.0.0。" +
            "Android 默认禁止明文 HTTP，需要在设置里打开「允许 HTTP」并接受提示。",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(12.dp))
    Text("3. 其他 OpenAI 兼容端点", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    Text(
        "只要服务提供 /chat/completions 接口即可。不要求支持函数调用或 JSON 模式。",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "提示：配置完成后可用「测试连接」验证。失败时会显示具体原因（DNS、超时、401、模型不存在等）。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
