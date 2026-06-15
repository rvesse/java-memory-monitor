package com.github.rvesse.java.memory.monitor.nmt;

import lombok.*;

import java.time.Instant;

@Builder
@Getter
@ToString
@EqualsAndHashCode
public class NMTReport {

    private long pid;
    private Instant timestamp;
    private boolean enabled;
    private LabelledMemoryAmount memoryUsage;
}
