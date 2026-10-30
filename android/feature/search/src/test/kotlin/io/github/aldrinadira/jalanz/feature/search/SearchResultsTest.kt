package io.github.aldrinadira.jalanz.feature.search

import io.github.aldrinadira.jalanz.core.searchclient.Place
import io.github.aldrinadira.jalanz.core.searchclient.PlaceSearch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchResultsTest {
    private val midValley = Place("Mid Valley Megamall", "Kuala Lumpur", 3.1177, 101.6774)

    private class FakeSearch(private val answer: (String) -> List<Place>) : PlaceSearch {
        val queries = mutableListOf<String>()
        override suspend fun search(query: String, near: Place?): List<Place> {
            queries += query
            return answer(query)
        }
    }

    @Test
    fun `searches once typing pauses, not on every keystroke`() = runTest {
        val search = FakeSearch { listOf(midValley) }
        val queries = MutableStateFlow("")
        val seen = mutableListOf<SearchResults>()
        backgroundScope.launch { searchResults(queries, search).collect { seen += it } }

        for (text in listOf("m", "mi", "mid", "mid v", "mid valley")) {
            queries.value = text
            advanceTimeBy(100)
        }
        advanceTimeBy(SEARCH_DEBOUNCE_MS)
        runCurrent()

        assertEquals(listOf("mid valley"), search.queries)
        assertEquals(SearchResults.Loaded(listOf(midValley)), seen.last())
    }

    @Test
    fun `short or blank query shows idle without searching`() = runTest {
        val search = FakeSearch { error("must not search") }
        val queries = MutableStateFlow(" k ")
        val seen = mutableListOf<SearchResults>()
        backgroundScope.launch { searchResults(queries, search).collect { seen += it } }
        runCurrent()
        assertEquals(listOf<SearchResults>(SearchResults.Idle), seen)
    }

    @Test
    fun `network failure becomes Failed instead of crashing`() = runTest {
        val search = FakeSearch { throw IOException("offline") }
        val queries = MutableStateFlow("klcc")
        val seen = mutableListOf<SearchResults>()
        backgroundScope.launch { searchResults(queries, search).collect { seen += it } }
        advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(listOf(SearchResults.Loading, SearchResults.Failed), seen)
    }
}
