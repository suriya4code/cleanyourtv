package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherTest {

    @Test
    public void theLineRoundsAndNamesTheSky() {
        assertEquals("\u2601\uFE0F  48°  Cloudy     H:52°  L:41°", Weather.line(3, false, 47.6, 51.5, 40.6));
    }

    @Test
    public void clearIsTheSunByDayAndTheMoonByNight() {
        assertTrue(Weather.symbol(0, true).startsWith("\u2600"));
        assertTrue(Weather.symbol(0, false).startsWith("\u263E"));
    }

    @Test
    public void everyWmoFamilyHasAWordAndSymbol() {
        int[] codes = {0, 1, 2, 3, 45, 48, 51, 56, 61, 63, 65, 66, 71, 73, 75, 77, 80, 82, 85, 95, 96, 99};
        for (int c : codes) {
            assertTrue("no word for " + c, !Weather.describe(c).isEmpty());
        }
        assertEquals("\u2602\uFE0F", Weather.symbol(81, true));
        assertEquals("\u2744\uFE0F", Weather.symbol(85, true));
        assertEquals("\u26A1\uFE0F", Weather.symbol(95, true));
    }
}
