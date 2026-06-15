package com.github.rvesse.java.memory.monitor.nmt;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@ToString
@EqualsAndHashCode
public class NMTReportSequence {

    @Builder.Default
    private final List<NMTReport> reports = new ArrayList<>();

    public boolean hasReports() {
        return !this.reports.isEmpty();
    }
}
