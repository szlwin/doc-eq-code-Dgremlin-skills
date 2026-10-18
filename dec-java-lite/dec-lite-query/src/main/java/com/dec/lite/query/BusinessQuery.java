package com.dec.lite.query;

import java.util.List;

/** Immutable query of current business stages. Ranges include both endpoints; they are not event history. */
public record BusinessQuery(String view, String stage, String start, String end, DirectoryQuery options) {
    public BusinessQuery {
        requireName(view);
        if (stage != null) requireName(stage);
        if (start != null) requireName(start);
        if (end != null) requireName(end);
        if (stage != null && (start != null || end != null))
            throw new IllegalArgumentException("eq cannot be combined with start/end");
        if (options == null) throw new IllegalArgumentException("query options are required");
        if (options.caseName() != null || options.from() != null || options.to() != null)
            throw new IllegalArgumentException("BusinessQuery uses eq/start/end, not DirectoryQuery selectors");
    }
    public static BusinessQuery find(String view) { return new BusinessQuery(view, null, null, null, DirectoryQuery.find(view)); }
    public BusinessQuery eq(String value) { requireName(value); return new BusinessQuery(view, value, start, end, options); }
    public BusinessQuery start(String value) { requireName(value); return new BusinessQuery(view, stage, value, end, options); }
    public BusinessQuery end(String value) { requireName(value); return new BusinessQuery(view, stage, start, value, options); }
    public BusinessQuery with(String relation) { requireName(relation); return withOptions(options.with(relation)); }
    public BusinessQuery where(String path, Object value) { return withOptions(options.where(path, value)); }
    public BusinessQuery whereSensitive(String path, Object value) { return withOptions(options.whereSensitive(path, value)); }
    public BusinessQuery whereIn(String path, List<?> values) { return withOptions(options.whereIn(path, values)); }
    public BusinessQuery page(int offset, int size) { return withOptions(options.page(offset, size)); }
    public BusinessQuery candidateLimit(int limit) { return withOptions(options.candidateLimit(limit)); }
    private BusinessQuery withOptions(DirectoryQuery value) { return new BusinessQuery(view, stage, start, end, value); }
    private static void requireName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("business query name is required");
    }
}
