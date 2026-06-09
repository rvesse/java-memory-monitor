package com.github.rvesse.java.memory.monitor.nmt.parser;

import com.github.rvesse.java.memory.monitor.nmt.AbstractTests;
import com.github.rvesse.java.memory.monitor.nmt.MemoryUnit;
import com.github.rvesse.java.memory.monitor.nmt.NMTReport;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.InputStream;

public class TestNmtReportParser extends AbstractTests {

    @Test
    public void givenReportInKb_whenParsing_thenParsedOk() throws Exception {
        // Given
        try (InputStream reportResource = resource(EXAMPLE_KB_SCALED)) {
            Assert.assertNotNull(reportResource);

            // When
            NMTReport report = NMTReportParser.parse(reportResource);

            // Then
            Assert.assertNotNull(report);
            Assert.assertEquals(report.getMemoryUsage().getReserved().getUnit(), MemoryUnit.KB);
        }
    }

    @Test
    public void givenReportInMBWithDiffs_whenParsing_thenParsedOk() throws Exception {
        // Given
        try (InputStream reportResource = resource(EXAMPLE_MB_SCALED_WITH_DIFFS)) {
            Assert.assertNotNull(reportResource);

            // When
            NMTReport report = NMTReportParser.parse(reportResource);

            // Then
            Assert.assertNotNull(report);
            Assert.assertEquals(report.getMemoryUsage().getReserved().getUnit(), MemoryUnit.MB);
        }
    }

    @Test
    public void givenReportInMBWithNegativeDiffs_whenParsing_thenParsedOk() throws Exception {
        // Given
        try (InputStream reportResource = resource(EXAMPLE_MB_SCALED_WITH_NEGATIVE_DIFFS)) {
            Assert.assertNotNull(reportResource);

            // When
            NMTReport report = NMTReportParser.parse(reportResource);

            // Then
            Assert.assertNotNull(report);
            Assert.assertEquals(report.getMemoryUsage().getReserved().getUnit(), MemoryUnit.MB);
            Assert.assertTrue(report.getMemoryUsage().getReservedDiff().getAmount() < 0);
        }
    }
}
