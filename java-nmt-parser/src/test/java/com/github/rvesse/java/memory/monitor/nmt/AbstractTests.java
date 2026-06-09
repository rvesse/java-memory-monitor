package com.github.rvesse.java.memory.monitor.nmt;

import com.github.rvesse.java.memory.monitor.nmt.parser.NMTReportParser;
import org.testng.Assert;
import org.testng.annotations.DataProvider;

import java.io.InputStream;

public class AbstractTests {
    public static final String EXAMPLE_KB_SCALED = "/example-nmt-kb.txt";
    public static final String EXAMPLE_MB_SCALED_WITH_DIFFS = "/example-nmt-mb-diffs.txt";
    public static final String EXAMPLE_MB_SCALED_WITH_NEGATIVE_DIFFS = "/example-nmt-mb-diffs2.txt";

    protected InputStream resource(String resource) {
        return this.getClass().getResourceAsStream(resource);
    }

    protected NMTReport parseReport(String resourceName) throws Exception {
        NMTReport report;
        try (InputStream input = resource(resourceName)) {
            report = NMTReportParser.parse(input);
        }
        Assert.assertNotNull(report);
        return report;
    }

    @DataProvider
    protected Object[][] resources() {
        return new Object[][] {
                { EXAMPLE_KB_SCALED },
                { EXAMPLE_MB_SCALED_WITH_DIFFS },
                { EXAMPLE_MB_SCALED_WITH_NEGATIVE_DIFFS }
        };
    }
}
