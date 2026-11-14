// SimilarRecommendation.kt

package com.bedivine.app.models

import com.music.innertube.models.YTItem
import com.bedivine.app.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
