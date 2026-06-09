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
public class TaggedMemoryUsage {

    /*
    e.g.

    (arena=164KB #4) (peak=45440KB #20)
    (arena=4067KB #1) (at peak)
    (malloc=1449KB #45873) (peak=1464KB #46484)
    (malloc=10763KB #46) (at peak)
    (malloc=2488KB +133KB, #72694 +4287)
    (mmap: reserved=98548KB, committed=35788KB, peak=98548KB)
     */

    @NonNull
    private String tag;
    @NonNull
    private MemoryAmount usage;
    private long count, peakCount;
    private MemoryAmount peak, diff;
    private Long countDiff, peakCountDiff;

    /**
     * Gets whether the memory usage includes a diff from previous baseline
     * @return True if difference present, false otherwise
     */
    public boolean hasDiff() {
        return this.diff != null;
    }

    /**
     * Gets whether the memory usage includes the peak usage
     * @return
     */
    public boolean hasPeak() {
        return this.peak != null;
    }
}
