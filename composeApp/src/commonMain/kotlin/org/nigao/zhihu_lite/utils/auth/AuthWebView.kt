package org.nigao.zhihu_lite.utils.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun AuthWebView(
    url: String,
    onAuthComplete: (String) -> Unit,
    modifier: Modifier = Modifier
)