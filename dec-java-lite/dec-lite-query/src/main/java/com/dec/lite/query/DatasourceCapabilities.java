package com.dec.lite.query;

public record DatasourceCapabilities(boolean joins, boolean distinctPaging, int maxBindParameters) {
    public static DatasourceCapabilities mysql() { return new DatasourceCapabilities(true, true, 65535); }
}
