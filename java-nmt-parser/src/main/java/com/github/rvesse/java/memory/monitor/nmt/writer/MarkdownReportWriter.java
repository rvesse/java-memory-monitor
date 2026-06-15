package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.github.rvesse.java.memory.monitor.nmt.*;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

@AllArgsConstructor
public class MarkdownReportWriter implements NMTReportWriter {

    @NonNull
    private final MemoryUnit outputScale;

    public MarkdownReportWriter() {
        this(MemoryUnit.MB);
    }

    private record WriterContext(BufferedWriter writer) implements AutoCloseable {
        private void write(String value) throws IOException {
            this.writer.write(value);
        }

        private void newLine() throws IOException {
            this.writer.write('\n');
        }

        @Override
        public void close() throws IOException {
            this.writer.flush();
            this.writer.close();
        }

        public void write(char c) throws IOException {
            this.writer.write(c);
        }
    }

    @Override
    public void write(NMTReport report, OutputStream output) throws IOException {
        try (WriterContext writer = new WriterContext(new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)))) {
            writer.write("# Java PID " + report.getPid());
            writer.newLine();
            writer.newLine();
            writeMemoryUsage(writer, report.getMemoryUsage(), 2);
        }
    }

    private void writeMemoryUsage(WriterContext writer, LabelledMemoryAmount memoryUsage, int headerLevel) throws
            IOException {
        writer.write(StringUtils.repeat('#', headerLevel));
        writer.write(' ');
        writer.write(memoryUsage.getLabel());
        writer.newLine();
        writer.newLine();
        writeMemoryAmount(writer, "Reserved", memoryUsage.getReserved(), memoryUsage.getReservedDiff());
        writeMemoryAmount(writer, "Committed", memoryUsage.getCommitted(), memoryUsage.getCommittedDiff());
        if (memoryUsage.hasDetailedMemoryUsages() || memoryUsage.hasDetailedMemoryUsages()) {
            writer.write("Detailed Memory Usage:");
            writer.newLine();
            if (memoryUsage.hasDetailedMemoryUsages()) {
                for (DetailedMemoryUsage detailedMemoryUsage : memoryUsage.getDetailedMemoryUsages()) {
                    writeDetailedMemoryUsage(writer, detailedMemoryUsage);
                }
            }
            if (memoryUsage.hasMemoryUsages()) {
                for (TaggedMemoryUsage taggedMemoryUsage : memoryUsage.getMemoryUsages()) {
                    writeTaggedMemoryUsage(writer, taggedMemoryUsage);
                }
            }

        }

        if (memoryUsage.hasCounts()) {
            writer.write("Counts: ");
            writer.newLine();
            for (TaggedCount count : memoryUsage.getCounts()) {
                writeCount(writer, count);
            }
        }

        if (memoryUsage.hasSubCategories()) {
            for (LabelledMemoryAmount subCategory : memoryUsage.getSubCategories()) {
                writer.newLine();
                writeMemoryUsage(writer, subCategory, headerLevel + 1);
            }
        }
    }

    private void writeDetailedMemoryUsage(WriterContext writer, DetailedMemoryUsage detailedMemoryUsage) throws
            IOException {
        writer.write("- ");
        writer.write(detailedMemoryUsage.getTag());
        writer.write(':');
        writer.newLine();
        writeMemoryAmount(writer, "  - Reserved Memory", detailedMemoryUsage.getReserved(), null);
        writeMemoryAmount(writer, "  - Committed Memory", detailedMemoryUsage.getCommitted(), null);
        if (detailedMemoryUsage.getPeak() != null) {
            writeMemoryAmount(writer, "  - Peak Memory", detailedMemoryUsage.getPeak(), null);
        }
    }

    private void writeTaggedMemoryUsage(WriterContext writer, TaggedMemoryUsage taggedMemoryUsage) throws IOException {
        writer.write("- ");
        writer.write(taggedMemoryUsage.getTag());
        writer.write(':');
        writer.newLine();
        writeMemoryAmount(writer, "  - Memory", taggedMemoryUsage.getUsage(), taggedMemoryUsage.getDiff());
        writer.write("  - Allocations: ");
        writer.write(String.format("%,d", taggedMemoryUsage.getCount()));
        if (taggedMemoryUsage.getCountDiff() != null) {
            writer.write(" (");
            if (taggedMemoryUsage.getCountDiff() > 0) {
                writer.write("+");
            }
            writer.write(String.format("%,d", taggedMemoryUsage.getCountDiff()));
            writer.write(")");
        }
        writer.newLine();
        if (taggedMemoryUsage.hasPeak()) {
            writeMemoryAmount(writer, "  - Peak Memory", taggedMemoryUsage.getPeak(), null);
            writer.write("  - Peak Allocations: ");
            writer.write(String.format("%,d", taggedMemoryUsage.getPeakCount()));
            if (taggedMemoryUsage.getPeakCountDiff() != null) {
                writer.write(" (");
                if (taggedMemoryUsage.getPeakCountDiff() > 0) {
                    writer.write("+");
                }
                writer.write(String.format("%,d", taggedMemoryUsage.getPeakCountDiff()));
                writer.write(")");
            }
            writer.newLine();
        }
    }

    private void writeCount(WriterContext writer, TaggedCount count) throws IOException {
        writer.write("- ");
        writer.write(count.getTag());
        writer.write(": ");
        writer.write(String.format("%,d", count.getCount()));
        writer.newLine();
    }

    private void writeMemoryAmount(WriterContext writer, String tag, MemoryAmount amount, MemoryAmount diff) throws
            IOException {
        writer.write(tag);
        writer.write(": ");
        writer.write(String.format("%,d", amount.as(this.outputScale)));
        writer.write(this.outputScale.toString());
        if (diff != null) {
            writer.write(" (");
            writer.write(String.format("%,d", diff.as(this.outputScale)));
            writer.write(diff.getUnit().toString());
            writer.write(")");
        }
        writer.newLine();
    }
}
