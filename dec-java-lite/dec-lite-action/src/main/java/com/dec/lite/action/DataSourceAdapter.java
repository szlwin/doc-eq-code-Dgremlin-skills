package com.dec.lite.action;

import java.util.Objects;

/** Manually supplied, named DEC adapter implementation; the name is not a canonical logical DataSource. */
public record DataSourceAdapter(String name, DataOperationAdapter operations) {
    public DataSourceAdapter {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Adapter implementation name is required");
        Objects.requireNonNull(operations, "DataSource operations");
    }
}
