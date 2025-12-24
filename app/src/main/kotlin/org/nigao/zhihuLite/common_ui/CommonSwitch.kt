package org.nigao.zhihuLite.common_ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nigao.zhihuLite.basicTypeExtension.noRippleClickable

@Composable
fun CommonSwitch(
    modifier: Modifier = Modifier,
    options: List<String> = emptyList(),
    selectedIndex: Int = 0,
    onClick: (index: Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.background(
            color = Color.Gray.copy(alpha = 0.3f),
            shape = RoundedCornerShape(50)
        ).padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier.then(
                    if (isSelected) {
                        Modifier.background(
                            color = Color.White,
                            shape = RoundedCornerShape(50)
                        )
                    } else {
                        Modifier
                    }
                ).padding(horizontal = 8.dp, vertical = 2.dp)
                    .noRippleClickable {
                        onClick.invoke(index)
                    }
            ) {
                Text(
                    text = option,
                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}