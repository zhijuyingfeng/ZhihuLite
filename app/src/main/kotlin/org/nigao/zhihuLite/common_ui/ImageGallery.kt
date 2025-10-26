package org.nigao.zhihuLite.common_ui

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

@Composable
fun ImageGallery(
    imageUrls: List<String>,
    modifier: Modifier = Modifier
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(1),
        modifier = modifier
    ) {
        items(
            count = imageUrls.size,
            key = { imageUrls[it] }
        ) {
            val imageUrl = imageUrls[it]
            AsyncImage(
                model = imageUrl,
                contentDescription = imageUrl,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(4.dp))
            )
        }
    }
}