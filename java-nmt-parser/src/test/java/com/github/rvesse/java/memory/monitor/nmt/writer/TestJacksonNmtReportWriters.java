package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.github.rvesse.java.memory.monitor.nmt.AbstractTests;
import com.github.rvesse.java.memory.monitor.nmt.NMTReport;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class TestJacksonNmtReportWriters extends AbstractTests {

    @Test(dataProvider = "resources")
    public void givenNmtReport_whenWritingJson_thenOk(String resourceName) throws Exception {
        // Given
        NMTReport report = parseReport(resourceName);

        // When
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NMTReportWriter writer = new JsonReportWriter();
        writer.write(report, output);

        // Then
        Assert.assertNotEquals(output.size(), 0);
        String json = output.toString(StandardCharsets.UTF_8);
        Assert.assertNotNull(json);
    }

    @Test(dataProvider = "resources")
    public void givenNmtReport_whenWritingYaml_thenOk(String resourceName) throws Exception {
        // Given
        NMTReport report = parseReport(resourceName);

        // When
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NMTReportWriter writer = new YamlReportWriter();
        writer.write(report, output);

        // Then
        Assert.assertNotEquals(output.size(), 0);
        String yaml = output.toString(StandardCharsets.UTF_8);
        Assert.assertNotNull(yaml);
    }

}
