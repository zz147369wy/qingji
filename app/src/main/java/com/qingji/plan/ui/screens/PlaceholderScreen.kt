package com.qingji.plan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.ThemePalette

/**
 * 占位页面。
 *
 * P0 阶段用来验证导航切换正常；P1–P5 会把每个页面逐个替换成真东西。
 */
@Composable
fun PlaceholderScreen(
    title: String,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("placeholder_$title"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = AppText.title,
            color = palette.text,
        )
        Text(
            text = "待实现",
            style = AppText.sub,
            color = palette.text3,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
