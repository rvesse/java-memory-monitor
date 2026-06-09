package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rvesse.java.memory.monitor.nmt.NMTReport;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

public class AbstractJacksonWriter implements NMTReportWriter {
    protected final ObjectMapper mapper;

    public AbstractJacksonWriter(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Jackson ObjectMapper cannot be null");
    }

    public void write(NMTReport report, OutputStream output) throws IOException {
        mapper.writeValue(output, report);
    }
}
