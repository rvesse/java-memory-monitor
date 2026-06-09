package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class YamlReportWriter extends AbstractJacksonWriter{

    public YamlReportWriter() {
        super(new YAMLMapper());
    }
}
