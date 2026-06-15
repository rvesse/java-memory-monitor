package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.github.rvesse.java.memory.monitor.nmt.*;
import lombok.Builder;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Builder
public class CsvReportSequenceWriter implements NMTReportSequenceWriter {

    @Builder.Default
    private final CSVFormat format = CSVFormat.DEFAULT;
    @Builder.Default
    private final MemoryUnit scale = MemoryUnit.MB;

    @Override
    public void write(NMTReportSequence reports, OutputStream output) throws IOException {
        // Determine headers
        Set<String> headers = new LinkedHashSet<>();
        Set<String> labels = new LinkedHashSet<>();
        headers.add("Timestamp");
        headers.add("Total Reserved");
        headers.add("Total Committed");
        for (NMTReport report : reports.getReports()) {
            if (report.getMemoryUsage().hasSubCategories()) {
                for (LabelledMemoryAmount usage : report.getMemoryUsage().getSubCategories()) {
                    headers.add(usage.getLabel() + " Reserved");
                    headers.add(usage.getLabel() + " Committed");
                    labels.add(usage.getLabel());
                }
            }
        }

        try (CSVPrinter printer = this.format.builder()
                                             .setHeader(headers.toArray(new String[0]))
                                             .get()
                                             .print(new OutputStreamWriter(output))) {
            for (NMTReport report : reports.getReports()) {
                List<Object> row = new ArrayList<>();

                row.add(report.getTimestamp());
                row.add(report.getMemoryUsage().getReserved().as(this.scale));
                row.add(report.getMemoryUsage().getCommitted().as(this.scale));

                for (String label : labels) {
                    LabelledMemoryAmount usage = report.getMemoryUsage().getSubCategory(label);
                    if (usage != null) {
                        row.add(usage.getReserved().as(this.scale));
                        row.add(usage.getCommitted().as(this.scale));
                    } else {
                        row.add(null);
                        row.add(null);
                    }
                }
                printer.printRecord(row);
            }

            printer.flush();
        }
    }
}
