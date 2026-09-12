package org.nigao.zhihuLite.business_ui.shared

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.nigao.zhihuLite.base_ui.noRippleClickable

@Composable
fun ImageGallery(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit,
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(1),
        modifier = modifier
    ) {
        items(
            count = imageUrls.size,
            // Images carry no server id; the pair below is unique within the gallery even
            // when the same URL appears twice.
            key = { index -> "$index:${imageUrls[index]}" },
            contentType = { "image" },
        ) { index ->
            val imageUrl = imageUrls[index]
            AsyncImage(
                model = imageUrl,
                contentDescription = imageUrl,
                contentScale = ContentScale.Crop,
                // An explicit, bounded size gives Coil a decode target and prevents the
                // gallery from requesting full-resolution bitmaps.
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(4f / 3f)
                    .noRippleClickable {
                        onClick(index)
                    }
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}
