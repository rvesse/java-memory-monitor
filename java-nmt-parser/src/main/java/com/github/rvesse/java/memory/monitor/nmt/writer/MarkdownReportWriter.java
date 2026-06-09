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

    @Override
    public void write(NMTReport report, OutputStream output) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.write("# Java PID " + report.getPid());
            newLine(writer);
            newLine(writer);
            writeMemoryUsage(writer, report.getMemoryUsage(), 2);
            writer.flush();
        }
    }

    private static void newLine(BufferedWriter writer) throws IOException {
        writer.write('\n');
    }

    private void writeMemoryUsage(BufferedWriter writer, LabelledMemoryAmount memoryUsage, int headerLevel) throws
            IOException {
        writer.write(StringUtils.repeat('#', headerLevel));
        writer.write(' ');
        writer.write(memoryUsage.getLabel());
        newLine(writer);
        newLine(writer);
        writeMemoryAmount(writer, "Reserved", memoryUsage.getReserved(), memoryUsage.getReservedDiff());
        writeMemoryAmount(writer, "Committed", memoryUsage.getCommitted(), memoryUsage.getCommittedDiff());
        if (memoryUsage.hasDetailedMemoryUsages() || memoryUsage.hasDetailedMemoryUsages()) {
            writer.write("Detailed Memory Usage:");
            newLine(writer);
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
            newLine(writer);
            for (TaggedCount count : memoryUsage.getCounts()) {
                writeCount(writer, count);
            }
        }

        if (memoryUsage.hasSubCategories()) {
            for (LabelledMemoryAmount subCategory : memoryUsage.getSubCategories()) {
                newLine(writer);
                writeMemoryUsage(writer, subCategory, headerLevel + 1);
            }
        }
    }

    private void writeDetailedMemoryUsage(BufferedWriter writer, DetailedMemoryUsage detailedMemoryUsage) throws
            IOException {
        writer.write("- ");
        writer.write(detailedMemoryUsage.getTag());
        writer.write(':');
        newLine(writer);
        writeMemoryAmount(writer, "  - Reserved Memory", detailedMemoryUsage.getReserved(), null);
        writeMemoryAmount(writer, "  - Committed Memory", detailedMemoryUsage.getCommitted(), null);
        if (detailedMemoryUsage.getPeak() != null) {
            writeMemoryAmount(writer, "  - Peak Memory", detailedMemoryUsage.getPeak(), null);
        }
    }

    private void writeTaggedMemoryUsage(BufferedWriter writer, TaggedMemoryUsage taggedMemoryUsage) throws IOException {
        writer.write("- ");
        writer.write(taggedMemoryUsage.getTag());
        writer.write(':');
        newLine(writer);
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
        newLine(writer);
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
            newLine(writer);
        }
    }

    private void writeCount(BufferedWriter writer, TaggedCount count) throws IOException {
        writer.write("- ");
        writer.write(count.getTag());
        writer.write(": ");
        writer.write(String.format("%,d", count.getCount()));
        newLine(writer);
    }

    private void writeMemoryAmount(BufferedWriter writer, String tag, MemoryAmount amount, MemoryAmount diff) throws
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
        newLine(writer);
    }
}
