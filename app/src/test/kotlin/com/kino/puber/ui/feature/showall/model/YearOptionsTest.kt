package com.kino.puber.ui.feature.showall.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YearOptionsTest {

    @Test
    fun yearOptions_listsThisYearLastYearDecadesAndBefore1980() {
        assertEquals(
            listOf(
                2026..2026,
                2025..2025,
                2020..2029,
                2010..2019,
                2000..2009,
                1990..1999,
                1980..1989,
                1900..1979,
            ),
            yearOptions(2026),
        )
    }

    @Test
    fun yearOptions_inTheFirstYearOfADecade_startsTheDecadesWithIt() {
        val options = yearOptions(2030)

        assertEquals(listOf(2030..2030, 2029..2029), options.take(2))
        assertEquals(listOf(2030..2039, 2020..2029), options.drop(2).take(2))
    }
}
