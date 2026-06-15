package com.github.rvesse.java.memory.monitor.nmt;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Jacksonized
public class LabelledMemoryAmount {

    /* e.g.
    Total: reserved=3881481KB, committed=377145KB
           malloc: 68329KB #328039
           mmap:   reserved=3813152KB, committed=308816KB
    */

    @NonNull
    private String label;
    @NonNull
    private MemoryAmount reserved, committed;
    private MemoryAmount reservedDiff, committedDiff;

    @Singular
    private List<DetailedMemoryUsage> detailedMemoryUsages;
    @Singular
    private List<TaggedMemoryUsage> memoryUsages;
    @Singular
    private List<TaggedCount> counts;

    @Singular
    private List<LabelledMemoryAmount> subCategories;

    public boolean hasMemoryUsages() {
        return memoryUsages != null && !this.memoryUsages.isEmpty();
    }

    public boolean hasDetailedMemoryUsages() {
        return detailedMemoryUsages != null && !this.detailedMemoryUsages.isEmpty();
    }

    public boolean hasSubCategories() {
        return this.subCategories != null && !this.subCategories.isEmpty();
    }

    public boolean hasCounts() {
        return this.counts != null && !this.counts.isEmpty();
    }

    public LabelledMemoryAmount getSubCategory(String label) {
        if (!hasSubCategories()) {
            return null;
        }

        for (LabelledMemoryAmount subCategory : this.subCategories) {
            if (Objects.equals(subCategory.getLabel(), label)) {
                return subCategory;
            }
        }
        return null;
    }
}
