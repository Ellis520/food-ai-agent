package com.foodmoment.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.foodmoment.app.AppContainer
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.components.SectionCard

/**
 * 高德 JS API 地图（WebView 内加载，不经过后端）。
 *
 * 关键点：
 *  1. 安全密钥必须在 loader.js 之前写入 window._AMapSecurityConfig
 *  2. 只允许在线加载官方脚本，不本地打包
 *  3. 页面用 file:// 不行（会丢 https 上下文），这里用 loadDataWithBaseURL 给一个 https 基址
 */
@Composable
fun MapScreen(container: AppContainer, nav: Navigator, targetLng: Double?, targetLat: Double?, title: String) {
    val settings = container.settings
    val jsKey = settings.amapJsKey
    val securityCode = settings.amapJsSecurityCode
    val center = if (targetLng != null && targetLat != null) {
        String.format("%.6f,%.6f", targetLng, targetLat)
    } else {
        settings.amapCenter
    }
    val zoom = if (targetLng != null) 16 else 14
    var error by remember { mutableStateOf<String?>(null) }

    if (jsKey.isNullOrBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SectionCard(title = "还没配置地图 Key") {
                Text(
                    "去「我的 → 高德地图」填写 Web端(JS API) Key；" +
                        "2021-12-02 之后申请的 Key 还要填安全密钥，否则地图会报 INVALID_USER_SCODE。",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "点这里去设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                androidx.compose.material3.TextButton(onClick = { nav.go(com.foodmoment.app.ui.Screen.Settings) }) {
                    Text("打开设置")
                }
            }
        }
        return
    }

    val html = remember(jsKey, securityCode, center, zoom, title) {
        buildMapHtml(jsKey, securityCode, center, zoom, title)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // 注意：WebView.settings 必须显式写，避免和同名局部变量冲突
                    this.settings.javaScriptEnabled = true
                    this.settings.domStorageEnabled = true
                    this.settings.setSupportZoom(true)
                    this.settings.builtInZoomControls = true
                    this.settings.displayZoomControls = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            // 把 JS 报错抛出来，否则地图白屏时完全不知道发生了什么
                            val text = consoleMessage.message()
                            if (text.contains("INVALID_USER", true) || text.contains("error", true)) {
                                error = text.take(200)
                            }
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            // 地图页面内不需要跳转；外链一律拦下，避免误触跳出 WebView
                            return true
                        }
                    }
                    loadDataWithBaseURL(
                        "https://webapi.amap.com/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )
        if (error != null) {
            Text(
                "地图加载出错：${error}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }
    }
}

/**
 * 把任意字符串安全地渲染成 JS 单引号字面量。
 *
 * 只转义双引号是不够的：商家名里出现单引号（如 "McDonald's"）会提前闭合字面量，
 * 导致整段脚本语法错误、地图白屏。这里把单引号、反斜杠、换行以及 `</script>` 都处理掉。
 */
private fun jsString(raw: String?): String {
    if (raw.isNullOrBlank()) return "''"
    val body = raw
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\r", "")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        // 防止内容提前闭合 <script> 标签
        .replace("</", "<\\/")
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
    return "'$body'"
}

private fun buildMapHtml(
    key: String,
    securityCode: String?,
    center: String,
    zoom: Int,
    title: String
): String {
    val safeKey = jsString(key)
    val safeCode = jsString(securityCode)
    val safeCenter = jsString(center)
    val safeTitle = jsString(title)
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="initial-scale=1.0,maximum-scale=1.0,user-scalable=no"/>
<title>地图</title>
<style>
  html,body,#container{margin:0;padding:0;width:100%;height:100%;background:#111;}
  #hint{position:absolute;left:8px;bottom:8px;color:#8ab;font:12px/1.4 sans-serif;
        background:rgba(0,0,0,.55);padding:4px 8px;border-radius:6px;}
</style>
</head>
<body>
<div id="container"></div>
<div id="hint"></div>
<script>
  // 安全密钥必须在 loader 之前设置
  var code = $safeCode;
  if (code) { window._AMapSecurityConfig = { securityJsCode: code }; }
  function fail(msg){
    document.getElementById('hint').textContent = '地图加载失败：' + msg;
    document.title = 'ERROR:' + msg;
  }
</script>
<script src="https://webapi.amap.com/loader.js"></script>
<script>
  (function(){
    var center = $safeCenter.split(',');
    var lng = parseFloat(center[0]);
    var lat = parseFloat(center[1]);
    var title = $safeTitle;
    if (!AMapLoader) { fail('loader.js 没能加载（请检查网络）'); return; }
    AMapLoader.load({
      key: $safeKey,
      version: '2.0',
      plugins: ['AMap.Scale', 'AMap.ToolBar']
    }).then(function(AMap){
      var map = new AMap.Map('container', {
        zoom: $zoom,
        zooms: [3, 20],
        center: [lng, lat],
        viewMode: '2D'
      });
      if (title) {
        new AMap.Marker({
          position: [lng, lat],
          title: title,
          map: map
        });
        // 地图上再压一条名字，免得只有针不知道是哪家
        var label = document.createElement('div');
        label.textContent = title;
        label.style.cssText = 'color:#fff;background:rgba(0,0,0,.6);padding:2px 6px;' +
                              'border-radius:4px;font:12px/1.4 sans-serif;white-space:nowrap;';
        new AMap.Marker({ position: [lng, lat], content: label, offset: new AMap.Pixel(-20, -34), map: map });
      }
      map.addControl(new AMap.Scale());
      document.getElementById('hint').textContent = '';
    }).catch(function(e){
      fail((e && (e.message || e)) || '未知错误');
    });
  })();
</script>
</body>
</html>
""".trimIndent()
}
