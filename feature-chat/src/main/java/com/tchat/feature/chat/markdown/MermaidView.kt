package com.tchat.feature.chat.markdown

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * Mermaid图表渲染组件
 *
 * 借鉴cherry-studio的设计思路：
 * - 使用WebView加载Mermaid.js进行渲染
 * - 防抖渲染：避免流式输出时频繁刷新
 * - 支持深色/浅色主题适配
 * - 错误处理和加载状态管理
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidView(
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()

    // Mermaid主题映射
    val mermaidTheme = if (isDarkTheme) "dark" else "default"

    // 防抖：延迟渲染，等待内容稳定
    var debouncedCode by remember { mutableStateOf(code) }
    var viewHeight by remember { mutableIntStateOf(200) }

    LaunchedEffect(code) {
        delay(300) // 300ms防抖延迟
        debouncedCode = code
    }

    // 生成HTML内容（使用防抖后的code）
    val htmlContent = remember(debouncedCode, mermaidTheme, backgroundColor) {
        generateMermaidHtml(debouncedCode, mermaidTheme, backgroundColor)
    }

    // WebView实例缓存
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { viewHeight.toDp() }),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setBackgroundColor(Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 渲染完成后调整高度
                            view?.evaluateJavascript(
                                "(function() { return document.body.scrollHeight; })();"
                            ) { height ->
                                val h = height.replace("\"", "").toIntOrNull() ?: 0
                                if (h > 0) {
                                    val newHeight = (h * context.resources.displayMetrics.density).toInt()
                                    if (newHeight != viewHeight) {
                                        viewHeight = newHeight
                                    }
                                }
                            }
                        }
                    }
                    webViewRef.value = this
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        )
    }
}

/**
 * 生成包含Mermaid图表的HTML
 */
private fun generateMermaidHtml(
    code: String,
    theme: String,
    backgroundColor: Int
): String {
    val bgColorHex = String.format("#%06X", 0xFFFFFF and backgroundColor)
    val escapedCode = code
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")
        .replace("\n", "\\n")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                html, body {
                    width: 100%;
                    background-color: $bgColorHex;
                    overflow-x: auto;
                }
                #container {
                    display: flex;
                    justify-content: center;
                    padding: 16px;
                    min-height: 100px;
                }
                #mermaid-diagram {
                    max-width: 100%;
                }
                #mermaid-diagram svg {
                    max-width: 100%;
                    height: auto;
                }
                .error {
                    color: #ff6b6b;
                    padding: 16px;
                    font-family: monospace;
                    font-size: 14px;
                    white-space: pre-wrap;
                    word-break: break-word;
                }
            </style>
        </head>
        <body>
            <div id="container">
                <div id="mermaid-diagram"></div>
            </div>
            <script src="file:///android_asset/mermaid.min.js"></script>
            <script>
                document.addEventListener('DOMContentLoaded', function() {
                    mermaid.initialize({
                        startOnLoad: false,
                        theme: '$theme',
                        securityLevel: 'loose',
                        flowchart: {
                            useMaxWidth: true,
                            htmlLabels: true
                        }
                    });

                    const code = `$escapedCode`;
                    const container = document.getElementById('mermaid-diagram');

                    mermaid.render('diagram', code)
                        .then(function(result) {
                            container.innerHTML = result.svg;
                        })
                        .catch(function(error) {
                            container.innerHTML = '<div class="error">Mermaid Error: ' +
                                error.message.replace(/</g, '&lt;').replace(/>/g, '&gt;') +
                                '</div>';
                        });
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}
