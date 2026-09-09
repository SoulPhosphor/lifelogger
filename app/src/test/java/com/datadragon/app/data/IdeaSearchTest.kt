package com.datadragon.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaSearchTest {

    private fun matcher(query: String, wholeWord: Boolean = false, matchCase: Boolean = false) =
        IdeaSearch.matcher(query, wholeWord, matchCase)!!

    @Test
    fun defaultSearchIsCaseInsensitiveAndPartial() {
        val m = matcher("drag")
        assertTrue(m.matches("dragon"))
        assertTrue(m.matches("drag"))
        assertTrue(m.matches("DataDragon"))
        assertFalse(m.matches("nothing here"))
    }

    @Test
    fun matchCaseRequiresExactCasing() {
        val m = matcher("Drag", matchCase = true)
        assertTrue(m.matches("Dragon"))
        assertFalse(m.matches("dragon"))
    }

    @Test
    fun wholeWordDoesNotMatchInsideALongerWord() {
        val m = matcher("drag", wholeWord = true)
        assertTrue(m.matches("a drag race"))
        assertTrue(m.matches("drag"))
        assertFalse(m.matches("dragon"))
        assertFalse(m.matches("DataDragon"))
    }

    @Test
    fun wholeWordMatchesACompletePhrase() {
        val m = matcher("data dragon", wholeWord = true)
        assertTrue(m.matches("the data dragon flies"))
        assertFalse(m.matches("the data dragonfly"))
    }

    @Test
    fun queryTextIsNeverTreatedAsRegex() {
        // Without escaping, "a.c" would match "abc" and "(x)" would be a group.
        assertFalse(matcher("a.c").matches("abc"))
        assertTrue(matcher("a.c").matches("xa.cy"))
        assertTrue(matcher("(draft)", wholeWord = true).matches("a (draft) idea"))
        assertTrue(matcher("c++", wholeWord = true).matches("about c++ here"))
    }

    @Test
    fun aBlankQueryRunsNoSearchAtAll() {
        assertNull(IdeaSearch.matcher("", wholeWord = false, matchCase = false))
        assertNull(IdeaSearch.matcher("   ", wholeWord = false, matchCase = false))
    }
}
