package com.tiberiptv.fire

import org.junit.Assert.assertEquals
import org.junit.Test

class RatingParsingTest {
    @Test
    fun numericRatingParsesCommonProviderFormats() {
        assertEquals(8.2f, numericRating("8.2"), 0.001f)
        assertEquals(8.2f, numericRating("8,2"), 0.001f)
        assertEquals(8.2f, numericRating("8.2/10"), 0.001f)
        assertEquals(8.4f, numericRating("4.2/5"), 0.001f)
        assertEquals(8.7f, numericRating("87%"), 0.001f)
        assertEquals(8.1f, numericRating("IMDb 8.1"), 0.001f)
    }

    @Test
    fun numericRatingRejectsEmptyOrInvalidValues() {
        assertEquals(0f, numericRating(""), 0.001f)
        assertEquals(0f, numericRating("null"), 0.001f)
        assertEquals(0f, numericRating("N/A"), 0.001f)
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

        val filtered = filteredRows(rows, "", false, false, false, CatalogSort.RECENT)

        assertEquals(listOf("high", "low"), filtered.single().items.map { it.id })
    }

    @Test
    fun filteredRowsSearchIgnoresAccentsAndSeparators() {
        val item = streamItem("1", "L'Été Bleu", "1000", "7.4")
        val rows = listOf(XtreamModels.ContentRow("Films français", listOf(item)))

        val filtered = filteredRows(rows, "ete bleu", false, false, false, CatalogSort.RECENT)

        assertEquals("Résultats recherche", filtered.single().title)
        assertEquals(listOf("1"), filtered.single().items.map { it.id })
    }

    @Test
    fun filteredRowsSortsRegularRowsByRating() {
        val lowerRated = streamItem("low", "A Movie", "2000", "6.1")
        val higherRated = streamItem("high", "B Movie", "1000", "8.9")
        val rows = listOf(XtreamModels.ContentRow("Action", listOf(lowerRated, higherRated)))

        val filtered = filteredRows(rows, "", false, false, false, CatalogSort.RATING)

        assertEquals(listOf("high", "low"), filtered.single().items.map { it.id })
    }

    @Test
    fun displayContentRatingNormalizesProviderValues() {
        assertEquals("PEGI 12", displayContentRating("rating_12"))
        assertEquals("PEGI 13", displayContentRating("PG-13"))
        assertEquals(null, displayContentRating("null"))
    }

    @Test
    fun formatBytesUsesFrenchBinaryUnits() {
        assertEquals("512 o", formatBytes(512L))
        assertEquals("1,00 Ko", formatBytes(1024L))
        assertEquals("1,50 Mo", formatBytes(1536L * 1024L))
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
