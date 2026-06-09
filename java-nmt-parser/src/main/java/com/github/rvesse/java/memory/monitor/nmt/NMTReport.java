package com.github.rvesse.java.memory.monitor.nmt;

import lombok.*;

@Builder
@Getter
@ToString
@EqualsAndHashCode
public class NMTReport {

    private long pid;
    @NonNull
    private LabelledMemoryAmount memoryUsage;
}
