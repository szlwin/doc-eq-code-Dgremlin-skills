package com.dec.lite.action;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable application-scoped implementations. Selection is immutable and belongs to each execution. */
public final class DataSourceAdapterRegistry {
    private final Map<String, DataOperationAdapter> implementations;
    private DataSourceAdapterRegistry(Map<String, DataOperationAdapter> implementations) {
        this.implementations = Map.copyOf(implementations);
    }
    public static DataSourceAdapterRegistry from(Collection<DataSourceAdapter> providers) {
        Map<String, DataOperationAdapter> index = new LinkedHashMap<>();
        for (DataSourceAdapter provider : Objects.requireNonNull(providers, "DataSource adapters")) {
            Objects.requireNonNull(provider, "DataSource adapter");
            if (index.putIfAbsent(provider.name(), provider.operations()) != null)
                throw new IllegalArgumentException("Duplicate DataSource adapter implementation: " + provider.name());
        }
        return new DataSourceAdapterRegistry(index);
    }
    public DataSourceSelection select(Map<String, String> selection) {
        Map<String, DataOperationAdapter> routes = new LinkedHashMap<>();
        for (var entry : Objects.requireNonNull(selection, "DataSource selection").entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) throw new IllegalArgumentException("Logical DataSource name is required");
            DataOperationAdapter adapter = entry.getValue() == null ? null : implementations.get(entry.getValue());
            if (adapter == null) throw new IllegalArgumentException("Unknown DataSource adapter implementation: " + entry.getValue());
            routes.put(entry.getKey(), adapter);
        }
        return new DataSourceSelection(selection, routes);
    }
}
