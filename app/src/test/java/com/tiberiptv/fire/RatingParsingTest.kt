package com.tiberiptv.fire

import org.junit.Assert.assertEquals
import org.junit.Test

class RatingParsingTest {
    @Test
    fun numericRatingParsesCommonProviderFormats() {
        assertEquals(8.2f, invokeNumericRating("8.2"), 0.001f)
        assertEquals(8.2f, invokeNumericRating("8,2"), 0.001f)
        assertEquals(8.2f, invokeNumericRating("8.2/10"), 0.001f)
        assertEquals(8.4f, invokeNumericRating("4.2/5"), 0.001f)
        assertEquals(8.7f, invokeNumericRating("87%"), 0.001f)
        assertEquals(8.1f, invokeNumericRating("IMDb 8.1"), 0.001f)
    }

    @Test
    fun numericRatingRejectsEmptyOrInvalidValues() {
        assertEquals(0f, invokeNumericRating(""), 0.001f)
        assertEquals(0f, invokeNumericRating("null"), 0.001f)
        assertEquals(0f, invokeNumericRating("N/A"), 0.001f)
    }

    @Test
    fun filteredRowsKeepPremiumRowOrder() {
        val higherRatedOlder = streamItem("high", "High", "1000", "8.2")
        val lowerRatedNewer = streamItem("low", "Low", "2000", "7.1")
        val rows = listOf(
            XtreamModels.ContentRow(
                "__premium_TOP_RATED__Top notes 6 derniers mois",
                listOf(higherRatedOlder, lowerRatedNewer)
            )
        )

        val filtered = invokeFilteredRows(rows, CatalogSort.RECENT)

        assertEquals(listOf("high", "low"), filtered.single().items.map { it.id })
    }

    private fun invokeNumericRating(value: String?): Float {
        val method = Class
            .forName("com.tiberiptv.fire.MainActivityKt")
            .getDeclaredMethod("numericRating", String::class.java)
        method.isAccessible = true
        return method.invoke(null, value) as Float
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeFilteredRows(
        rows: List<XtreamModels.ContentRow>,
        sort: CatalogSort
    ): List<XtreamModels.ContentRow> {
        val method = Class
            .forName("com.tiberiptv.fire.MainActivityKt")
            .getDeclaredMethod(
                "filteredRows",
                List::class.java,
                String::class.java,
                java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE,
                CatalogSort::class.java
            )
        method.isAccessible = true
        return method.invoke(null, rows, "", false, false, false, sort) as List<XtreamModels.ContentRow>
    }

    private fun streamItem(
        id: String,
        title: String,
        addedTimestamp: String,
        rating: String
    ): XtreamModels.StreamItem =
        XtreamModels.StreamItem(
            id,
            title,
            XtreamModels.StreamItem.TYPE_MOVIE,
            "",
            "",
            "mp4",
            true,
            "",
            addedTimestamp,
            rating,
            "2026"
        )
}
