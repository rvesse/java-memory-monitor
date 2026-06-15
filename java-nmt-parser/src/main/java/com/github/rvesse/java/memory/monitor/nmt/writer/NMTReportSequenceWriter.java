package com.github.rvesse.java.memory.monitor.nmt.writer;

import com.github.rvesse.java.memory.monitor.nmt.NMTReportSequence;

import java.io.IOException;
import java.io.OutputStream;

public interface NMTReportSequenceWriter {

    void write(NMTReportSequence reports, OutputStream output) throws IOException;
}
