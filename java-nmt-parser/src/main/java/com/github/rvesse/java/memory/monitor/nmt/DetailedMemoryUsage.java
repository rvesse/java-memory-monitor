package com.github.rvesse.java.memory.monitor.nmt;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.lang.management.MemoryUsage;

@Getter
@ToString
@EqualsAndHashCode
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Jacksonized
public class DetailedMemoryUsage {

    /*
    e.g.
    (mmap: reserved=98548KB, committed=35788KB, peak=98548KB)
    (mmap: reserved=1986MB, committed=128MB)
     */

    @NonNull
    private String tag;
    @NonNull
    private MemoryAmount reserved, committed;
    private MemoryAmount peak;

    /**
     * Gets whether the memory usage includes the peak usage
     * @return
     */
    public boolean hasPeak() {
        return this.peak != null;
    }
}
