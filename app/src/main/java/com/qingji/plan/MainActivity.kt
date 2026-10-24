package com.qingji.plan

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.qingji.plan.ui.nav.HomeShell
import com.qingji.plan.ui.pet.DEFAULT_WHALE_SCHEME
import com.qingji.plan.ui.theme.RadialThemeTransition
import com.qingji.plan.ui.theme.ThemeMode
import com.qingji.plan.ui.theme.resolvePalette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // 主题 = 鲸鱼配色 × 明暗模式。设置页改了会通过 Flow 自动跟着变
            val app = QingJiApp.from(this)
            var themeTapAt by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
            val scheme by app.settingsRepo.observeWhaleScheme().collectAsState(initial = DEFAULT_WHALE_SCHEME)
            val mode by app.settingsRepo.observeThemeMode().collectAsState(initial = ThemeMode.LIGHT)
            val palette = resolvePalette(scheme, mode)

            // 状态栏 / 导航栏图标的深浅跟着 App 的明暗走，而不是跟系统走：
            // 系统是浅色、App 选了「深海夜」时，图标必须是白的
            LaunchedEffect(palette.isDark) {
                val style = if (palette.isDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            // 圆形换肤：点哪个配色就从哪扩散
            RadialThemeTransition(
                palette = palette,
                revealAt = themeTapAt,
                onRevealed = { themeTapAt = null },
            ) {
                HomeShell(
                    onThemeTapAt = { themeTapAt = it },
                )
            }
        }
    }
}
