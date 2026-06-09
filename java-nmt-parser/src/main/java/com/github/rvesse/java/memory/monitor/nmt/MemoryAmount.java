package com.github.rvesse.java.memory.monitor.nmt;

import lombok.*;

import java.util.Objects;

@AllArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class MemoryAmount {

    private long amount;
    @NonNull
    private MemoryUnit unit;

    public long as(MemoryUnit unit) {
        Objects.requireNonNull(unit);

        return switch (this.unit) {
            case KB -> switch (unit) {
                case KB -> this.amount;
                case MB -> this.amount / 1024;
                case GB -> this.amount / 1024 / 1024;
            };
            case MB -> switch (unit) {
                case KB -> this.amount * 1024;
                case MB -> this.amount;
                case GB -> this.amount / 1024;
            };
            case GB -> switch (unit) {
                case KB -> this.amount * 1024 * 1024;
                case MB -> this.amount * 1024;
                case GB -> this.amount;
            };
        };
    }
}
