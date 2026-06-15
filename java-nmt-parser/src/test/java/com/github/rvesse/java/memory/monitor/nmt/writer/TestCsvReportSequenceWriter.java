package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.github.rvesse.java.memory.monitor.nmt.NMTReportSequence;
import com.github.rvesse.java.memory.monitor.nmt.parser.NMTReportParser;
import org.apache.commons.lang3.StringUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class TestCsvReportSequenceWriter {

    @Test
    public void givenReportSequence_whenWritingCsv_thenWritten() throws Exception {
        // Given
        File reportDir = new File("src/test/files/");
        NMTReportSequence reports = NMTReportParser.parseReports(reportDir, "graph-0_graph_");

        // When
        CsvReportSequenceWriter writer = CsvReportSequenceWriter.builder().build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(reports, output);

        // Then
        String csv = output.toString(StandardCharsets.UTF_8);
        Assert.assertNotNull(csv);
        Assert.assertFalse(StringUtils.isBlank(csv));
    }
}
