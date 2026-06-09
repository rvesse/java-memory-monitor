package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.github.rvesse.java.memory.monitor.nmt.AbstractTests;
import com.github.rvesse.java.memory.monitor.nmt.MemoryUnit;
import com.github.rvesse.java.memory.monitor.nmt.NMTReport;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class TestMarkdownReportWriter extends AbstractTests {

    @Test(dataProvider = "resources")
    public void givenNmtReport_whenWritingMarkdown_thenOk(String resourceName) throws Exception {
        // Given
        NMTReport report = parseReport(resourceName);

        // When
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NMTReportWriter writer = new MarkdownReportWriter();
        writer.write(report, output);

        // Then
        Assert.assertNotEquals(output.size(), 0);
        String markdown = output.toString(StandardCharsets.UTF_8);
        Assert.assertNotNull(markdown);
        System.out.println(markdown);
    }

    @Test(dataProvider = "resources")
    public void givenNmtReport_whenWritingMarkdownInMBScale_thenOk(String resourceName) throws Exception {
        // Given
        NMTReport report = parseReport(resourceName);

        // When
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NMTReportWriter writer = new MarkdownReportWriter(MemoryUnit.MB);
        writer.write(report, output);

        // Then
        Assert.assertNotEquals(output.size(), 0);
        String markdown = output.toString(StandardCharsets.UTF_8);
        Assert.assertNotNull(markdown);
        Assert.assertFalse(markdown.contains(MemoryUnit.KB.toString()));
        Assert.assertTrue(markdown.contains(MemoryUnit.MB.toString()));
        Assert.assertFalse(markdown.contains(MemoryUnit.GB.toString()));
        System.out.println(markdown);
    }

    @Test(dataProvider = "resources")
    public void givenNmtReport_whenWritingMarkdownInGBScale_thenOk(String resourceName) throws Exception {
        // Given
        NMTReport report = parseReport(resourceName);

        // When
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NMTReportWriter writer = new MarkdownReportWriter(MemoryUnit.GB);
        writer.write(report, output);

        // Then
        Assert.assertNotEquals(output.size(), 0);
        String markdown = output.toString(StandardCharsets.UTF_8);
        Assert.assertNotNull(markdown);
        Assert.assertFalse(markdown.contains(MemoryUnit.KB.toString()));
        Assert.assertTrue(markdown.contains(MemoryUnit.GB.toString()));
        System.out.println(markdown);
    }
}
