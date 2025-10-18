package org.nigao.zhihu_lite.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun AuthWebView(
    url: String,
    onAuthComplete: (String) -> Unit,
    modifier: Modifier = Modifier
)