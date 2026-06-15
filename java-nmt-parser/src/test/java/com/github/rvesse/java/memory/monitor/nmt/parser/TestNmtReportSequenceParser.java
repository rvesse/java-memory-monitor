package com.github.rvesse.java.memory.monitor.nmt.parser;

import com.github.rvesse.java.memory.monitor.nmt.NMTReportSequence;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

public class TestNmtReportSequenceParser {

    @Test
    public void givenReportsDirectory_whenParsingSequence_thenAllParsed() throws Exception {
        // Given
        File reportDir = new File("src/test/files/");

        // When
        NMTReportSequence reports = NMTReportParser.parseReports(reportDir, null);

        // Then
        Assert.assertTrue(reports.hasReports());
        Assert.assertEquals(reports.getReports().size(), 15);
    }

    @Test
    public void givenReportsDirectory_whenParsingSequenceWithCorrectFilenamePrefix_thenAllParsed() throws Exception {
        // Given
        File reportDir = new File("src/test/files/");

        // When
        NMTReportSequence reports = NMTReportParser.parseReports(reportDir, "graph-0_graph_");

        // Then
        Assert.assertTrue(reports.hasReports());
        Assert.assertEquals(reports.getReports().size(), 15);
    }

    @Test
    public void givenReportsDirectory_whenParsingSequenceWithIncorrectFilenamePrefix_thenNothingParsed() throws Exception {
        // Given
        File reportDir = new File("src/test/files/");

        // When
        NMTReportSequence reports = NMTReportParser.parseReports(reportDir, "foo");

        // Then
        Assert.assertFalse(reports.hasReports());
    }
}
