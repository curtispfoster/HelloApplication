package com.example.helloapplication;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StagedTableTest {

    @Test
    void decimalCheckAgreesWithTheChartPattern() {
        for (String s : List.of("12", "-3.5", ".5", "5.", "1e6", "+2E-3", "0", ".", "", "-", "1e", "e5", "NaN",
                "0x1p3", "1.2.3", "1,5", " 1", "٣")) {
            assertEquals(ChartMaker.DECIMAL.matcher(s).matches(), StagedTable.isDecimal(s), s);
        }
    }

    @Test
    void integersMustFitInALong() {
        assertTrue(StagedTable.isInteger("-123"));
        assertTrue(StagedTable.isInteger("+123456789012345678"));
        assertFalse(StagedTable.isInteger("1234567890123456789"));
        assertFalse(StagedTable.isInteger("1.0"));
        assertFalse(StagedTable.isInteger("-"));
    }

    @Test
    void keyValuesWriteNumbersOneWay() {
        assertEquals(7L, StagedTable.keyValue(" 007 "));
        assertEquals(7L, StagedTable.keyValue("7.0"));
        assertEquals("7.5", StagedTable.keyValue("7.50"));
        assertEquals("12345678901234567890", StagedTable.keyValue("12345678901234567890"));
        assertEquals("SKU-7", StagedTable.keyValue("SKU-7 "));
    }
}
