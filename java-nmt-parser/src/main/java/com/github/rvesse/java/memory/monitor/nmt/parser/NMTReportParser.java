package com.github.rvesse.java.memory.monitor.nmt.parser;

import com.github.rvesse.java.memory.monitor.nmt.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/**
 * A parser for Java native memory tracking reports
 */
public class NMTReportParser implements AutoCloseable {

    private final BufferedReader reader;
    private long lineNumber = 0;
    private File inputFile = null;
    private Instant timestamp = null;

    NMTReportParser(InputStream input) {
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    NMTReportParser(File reportFile) throws FileNotFoundException {
        this(new FileInputStream(reportFile));
        this.inputFile = reportFile;

        // See if the filename includes a timestamp we can parse
        // e.g. search-projector-5c9c7c669-wb5dk_search-projector_native-memory-20260605_101024.txt
        String[] parts = reportFile.getName().split("-");
        if (parts.length > 1) {
            String lastPart = parts[parts.length - 1];
            lastPart = lastPart.substring(0, lastPart.indexOf('.'));

            try {
                TemporalAccessor parsed = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").parse(lastPart);
                Calendar date = Calendar.getInstance();
                date.set(parsed.get(ChronoField.YEAR_OF_ERA),
                          parsed.get(ChronoField.MONTH_OF_YEAR),
                          parsed.get(ChronoField.DAY_OF_MONTH),
                          parsed.get(ChronoField.HOUR_OF_DAY),
                          parsed.get(ChronoField.MINUTE_OF_HOUR),
                          parsed.get(ChronoField.MICRO_OF_SECOND));
                this.timestamp = date.toInstant();
            } catch (DateTimeParseException dtEx) {
                // Ignored, will try and use file creation time instead
            }
        }
        // If no valid timestamp in filename use file creation time (if available)
        if (this.timestamp == null) {
            try {
                this.timestamp =
                        Files.readAttributes(reportFile.toPath(), BasicFileAttributes.class)
                             .creationTime()
                             .toInstant();
            } catch (IOException ioEx) {
                // Ignored
            }
        }
    }

    public static NMTReport parse(File reportFile) throws Exception {
        try (NMTReportParser parser = new NMTReportParser(reportFile)) {
            return parser.parse();
        }
    }

    public static NMTReport parse(InputStream input) throws Exception {
        try (NMTReportParser parser = new NMTReportParser(input)) {
            return parser.parse();
        }
    }

    /**
     * Parses a sequence of reports from a directory
     *
     * @param reportDir      Report directory
     * @param filenamePrefix Filename prefix used to limit which reports are parsed
     * @return NMT Report sequence
     * @throws Exception Thrown if a parsing or IO error occurs
     */
    public static NMTReportSequence parseReports(File reportDir, String filenamePrefix) throws Exception {
        Objects.requireNonNull(reportDir, "Report Directory cannot be null");
        if (!reportDir.exists() || !reportDir.isDirectory()) {
            throw new IllegalArgumentException("Report Directory must be a directory that exists");
        }

        FilenameFilter filter;
        if (StringUtils.isNotBlank(filenamePrefix)) {
            filter = (dir, name) -> Strings.CS.startsWith(name, filenamePrefix);
        } else {
            filter = (dir, name) -> true;
        }
        File[] reportFiles = reportDir.listFiles(filter);
        if (reportFiles != null) {
            List<NMTReport> reports = new ArrayList<>();
            for (File reportFile : reportFiles) {
                reports.add(parse(reportFile));
            }
            reports.sort(Comparator.comparing(NMTReport::getTimestamp));

            return NMTReportSequence.builder().reports(reports).build();
        } else {
            throw new IllegalArgumentException(
                    "Failed to list any files from report directory " + reportDir.getAbsolutePath());
        }
    }

    public NMTReport parse() throws IOException {
        NMTReport.NMTReportBuilder builder = NMTReport.builder().timestamp(this.timestamp);

        String line;
        ParserState state = ParserState.Pid;
        LabelledMemoryAmount.LabelledMemoryAmountBuilder memory = LabelledMemoryAmount.builder().label("Total");
        LabelledMemoryAmount.LabelledMemoryAmountBuilder subCategoryMemory = null;
        LabelledMemoryAmount.LabelledMemoryAmountBuilder current = memory;
        String[] parts;
        try {
            while ((line = nextLine()) != null) {
                if (StringUtils.isBlank(line)) {
                    continue;
                }

                switch (state) {
                    case Pid:
                    /*
                    7:
                     */
                        parts = line.split(":", 1);
                        if (StringUtils.isNumeric(parts[0])) {
                            builder.pid(Long.parseLong(parts[0]));
                        } else if (Objects.equals(parts[0], "Native memory tracking is not enabled")) {
                            // Report against a Java process that didn't have the feature enabled so nothing to parse
                            return builder.enabled(false).build();
                        } else if (Objects.equals(parts[0], "Native Memory Tracking:")) {
                            state = ParserState.Memory;
                            builder.enabled(true);
                            continue;
                        }
                        break;
                    case Memory:
                        if (Strings.CI.startsWith(line, "(")) {
                            // Some preamble lines in the memory report have just a ( as first character
                            // Ignore and continue
                            continue;
                        } else if (Strings.CS.startsWith(line, "Total:")) {
                            // Start of memory usage report
                            parseTotal(memory, line.substring(line.indexOf(':') + 1));
                        } else if (Strings.CI.startsWith(line, "-")) {
                            // Start of a memory usage sub-category
                            if (subCategoryMemory != null) {
                                memory.subCategory(subCategoryMemory.build());
                            }
                            subCategoryMemory = LabelledMemoryAmount.builder();
                            current = subCategoryMemory;
                            parts = line.split("\\(", 2);
                            subCategoryMemory.label(StringUtils.stripStart(parts[0], "- ").trim());
                            parseTotal(subCategoryMemory, parts[1]);
                        } else if (Strings.CI.equals(line, "Virtual memory map:") || line.startsWith("[")) {
                            if (subCategoryMemory != null) {
                                memory.subCategory(subCategoryMemory.build());
                                subCategoryMemory = null;
                            }
                            state = ParserState.Details;
                            continue;
                        } else {
                            // This is a memory usage stat/count within a memory category
                        /*
                        e.g.
                            malloc: 68329KB #328039
                            mmap:   reserved=3813152KB, committed=308816KB

                            (threads #61)
                            (stack: reserved=62464KB, committed=5720KB, peak=5720KB)
                            (malloc=126KB #373) (peak=138KB #432)
                            (arena=71KB #121) (peak=1253KB #121)
                            (malloc=114MB +67MB #2 +1)
                         */
                            line = line.trim();
                            line = StringUtils.replaceChars(line, "()", "");
                            if (Strings.CS.startsWithAny(line, "malloc:", "malloc=", "arena=")) {
                                String tag = extractTag(line);
                                parseTaggedMemoryUsage(current, tag, line.substring(tag.length() + 1));
                            } else if (Strings.CS.startsWithAny(line, "mmap:", "stack:")) {
                                String tag = extractTag(line);
                                parseDetailedMemoryUsage(current, tag, line.substring(tag.length() + 2));
                            } else if (line.contains("#")) {
                                parseTaggedCounts(current, line);
                            }
                        }

                        break;
                    case Details:
                        // TODO
                        break;
                }
            }
        } finally {
            reader.close();
        }
        return builder.memoryUsage(memory.build()).build();
    }

    private void parseDetailedMemoryUsage(LabelledMemoryAmount.LabelledMemoryAmountBuilder current, String tag,
                                          String line) throws IOException {
        /*
        e.g.
        (mmap: reserved=3356672KB, committed=143360KB, peak=3356672KB)
        (stack: reserved=36MB, committed=1MB)
         */

        DetailedMemoryUsage.DetailedMemoryUsageBuilder builder = DetailedMemoryUsage.builder().tag(tag);

        String[] parts = line.split(",");
        for (String part : parts) {
            part = part.trim();
            String rawAmount = part.substring(part.indexOf('=') + 1);
            if (part.startsWith("reserved=")) {
                builder.reserved(parseMemoryAmount(rawAmount));
            } else if (part.startsWith("committed=")) {
                builder.committed(parseMemoryAmount(rawAmount));
            } else if (part.startsWith("peak=")) {
                builder.peak(parseMemoryAmount(rawAmount));
            }
        }

        current.detailedMemoryUsage(builder.build());
    }

    private void parseTaggedCounts(LabelledMemoryAmount.LabelledMemoryAmountBuilder current, String line) throws
            IOException {
        /*
        e.g.
        classes #806
        instance classes #673, array classes #133
        thread #18
         */
        line = line.trim();
        String[] parts;
        if (line.contains(",")) {
            // Multiple counts to parse
            parts = line.split(",");
            for (String part : parts) {
                if (StringUtils.isNotBlank(part)) {
                    parseTaggedCounts(current, part);
                }
            }
        } else {
            // Single count to parse
            TaggedCount.TaggedCountBuilder builder = TaggedCount.builder();
            parts = line.split("#");
            builder.tag(parts[0].trim()).count(parseCount(parts[1]));
            current.count(builder.build());
        }
    }

    private void parseTaggedMemoryUsage(LabelledMemoryAmount.LabelledMemoryAmountBuilder current, String tag,
                                        String line) throws
            IOException {
        /*
        e.g.
        126KB #373
        126KB #373 peak=138KB #432
        10763KB #46 at peak
        114MB +67MB #2 +1
        1MB #1302 +1
         */

        // Report may contain diffs
        boolean hasDiffs = line.contains("+") || line.contains("-");
        int diffAdj = 0;

        TaggedMemoryUsage.TaggedMemoryUsageBuilder builder = TaggedMemoryUsage.builder().tag(tag);
        String[] parts = line.trim().split("\\s+");
        MemoryAmount usage = parseMemoryAmount(parts[0]);
        builder.usage(usage);
        if (hasDiffs && !parts[1].startsWith("#")) {
            // Parse difference in memory usage
            builder.diff(parseMemoryAmount(parts[1]));
            diffAdj++;
        }
        long count = parseCount(parts[1 + diffAdj]);
        builder.count(count);
        if (hasDiffs) {
            // Parse difference in count
            builder.countDiff(parseCount(parts[2 + diffAdj]));
        }
        if (!hasDiffs) {
            // Peak information may be present in reports that don't contain diffs
            if (parts.length == 4) {
                if (Objects.equals(parts[2], "at") && Objects.equals(parts[3], "peak")) {
                    builder.peak(usage).peakCount(count);
                } else {
                    builder.peak(parseMemoryAmount(parts[2].substring(parts[2].indexOf('=') + 1)))
                           .peakCount(parseCount(parts[3]));
                }
            }
        }

        current.memoryUsage(builder.build());
    }

    private long parseCount(String rawCount) throws IOException {
        /*
        e.g.
        #1
        #46
        +1
        -17
         */
        try {
            if (rawCount.startsWith("#")) {
                return Long.parseLong(rawCount.substring(1));
            } else {
                return Long.parseLong(rawCount);
            }
        } catch (NumberFormatException e) {
            throw parseError("Bad count %s encountered", rawCount);
        }
    }

    private String extractTag(String line) {
        if (line.contains(":")) {
            return line.substring(0, line.indexOf(':'));
        } else {
            return line.substring(0, line.indexOf('='));
        }
    }

    /**
     * Gets the next available line or {@code null} if EOF
     * <p>
     * Also updates the tracked line number for error reporting.
     * </p>
     *
     * @return Next available line
     * @throws IOException Thrown if a next line cannot be read
     */
    private String nextLine() throws IOException {
        String next = reader.readLine();
        if (next != null) {
            lineNumber++;
        }
        return next;
    }

    private void parseTotal(LabelledMemoryAmount.LabelledMemoryAmountBuilder memory, String totalLine) throws
            IOException {
        /*
        e.g.
        Total: reserved=3881481KB, committed=377145KB
        Java Heap (reserved=3356672KB, committed=143360KB)
        Total: reserved=3575MB +67MB, committed=320MB +67MB

        NB - Calling code is expected to have already stripped off the label
         */
        totalLine = StringUtils.strip(totalLine, "()").trim();
        String[] parts = totalLine.split(",");
        for (String part : parts) {
            part = part.trim();
            int end = part.contains(" ") ? part.indexOf(' ') : part.length();
            boolean hasDiff = end < part.length();
            String rawAmount = part.substring(part.indexOf('=') + 1, end);
            if (Strings.CI.startsWith(part, "reserved=")) {
                memory.reserved(parseMemoryAmount(rawAmount));
                if (hasDiff) {
                    memory.reservedDiff(parseMemoryAmount(part.substring(end + 1)));
                }
            } else if (Strings.CI.startsWith(part, "committed=")) {
                memory.committed(parseMemoryAmount(rawAmount));
                if (hasDiff) {
                    memory.committedDiff(parseMemoryAmount(part.substring(end + 1)));
                }
            }
        }
    }

    private MemoryAmount parseMemoryAmount(String amountToParse) throws IOException {
        /*
        e.g.
        3356672KB
        1500MB
        1GB
        */

        String rawAmount = amountToParse.substring(0, amountToParse.length() - 2);
        String rawUnit = amountToParse.substring(amountToParse.length() - 2);

        try {
            long amount = Long.parseLong(rawAmount);
            MemoryUnit unit = MemoryUnit.valueOf(rawUnit);

            return new MemoryAmount(amount, unit);
        } catch (IllegalArgumentException e) {
            throw parseError("Bad memory amount %s encountered", amountToParse);
        }
    }

    private IOException parseError(String message, Object... args) {
        if (this.inputFile != null) {
            return new IOException(
                    "[" + this.inputFile.getAbsolutePath() + " Line " + this.lineNumber + "] " + String.format(message,
                                                                                                               args));
        } else {
            return new IOException("[Line " + this.lineNumber + "] " + String.format(message, args));
        }
    }

    @Override
    public void close() throws Exception {
        this.reader.close();
    }
}
