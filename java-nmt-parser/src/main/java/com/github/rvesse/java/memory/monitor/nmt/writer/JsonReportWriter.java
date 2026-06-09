package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonReportWriter extends AbstractJacksonWriter {

    public JsonReportWriter() {
        super(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }
}
