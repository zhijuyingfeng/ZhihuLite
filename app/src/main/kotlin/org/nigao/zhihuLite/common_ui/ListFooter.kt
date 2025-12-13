package org.nigao.zhihuLite.common_ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.basicTypeExtension.noRippleClickable

enum class ListFooterStatus() {
    IDLE, LOADING, NETWORK_FAILED, NO_MORE_DATA
}

@Composable
fun ListFooterStatus.text(): String? {
    return when (this) {
        ListFooterStatus.IDLE -> null
        ListFooterStatus.LOADING -> stringResource(R.string.list_footer_loading)
        ListFooterStatus.NETWORK_FAILED -> stringResource(R.string.list_footer_failed)
        ListFooterStatus.NO_MORE_DATA -> stringResource(R.string.list_footer_no_more)
    }
}

@Composable
fun ListFooter(
    loadMore: suspend () -> Unit,
    status: ListFooterStatus = ListFooterStatus.IDLE,
) {
    val coroutineScope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().height(40.dp)
            .onGloballyPositioned {
                if (it.isAttached && status == ListFooterStatus.IDLE) {
                    coroutineScope.launch {
                        loadMore.invoke()
                    }
                }
            }
            .noRippleClickable(
                enabled = status == ListFooterStatus.NETWORK_FAILED
            ) {
                coroutineScope.launch {
                    loadMore.invoke()
                }
            }
    ) {
        when(status) {
            ListFooterStatus.IDLE -> {}
            ListFooterStatus.LOADING -> {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                val text = status.text()
                text?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    ListFooterTipText(text)
                }
            }
            else -> {
                val text = status.text()
                text?.let {
                    ListFooterTipText(text)
                }
            }
        }
    }
}

@Composable
fun ListFooterTipText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        color = Color.LightGray,
        modifier = modifier
    )
}