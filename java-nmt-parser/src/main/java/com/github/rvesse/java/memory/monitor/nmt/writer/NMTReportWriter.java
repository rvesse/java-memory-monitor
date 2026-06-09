package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.github.rvesse.java.memory.monitor.nmt.NMTReport;

import java.io.IOException;
import java.io.OutputStream;

public interface NMTReportWriter {

    void write(NMTReport report, OutputStream output) throws IOException;
}
