package com.github.rvesse.java.memory.monitor.nmt;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

@Getter
@ToString
@EqualsAndHashCode
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Jacksonized
public class TaggedCount {

    /*
    e.g.

    (threads #61)
    (classes #17793)
    (  instance classes #16649, array classes #1144)
     */
    @NonNull
    private String tag;
    private long count;
    private Long diff;
}
