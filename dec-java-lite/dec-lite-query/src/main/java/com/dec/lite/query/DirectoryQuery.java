package com.dec.lite.query;

import java.util.ArrayList;
import java.util.List;

/** Immutable business query. eq selects a result case; where compares model fields. */
public record DirectoryQuery(String directory, String caseName, String from, String to,
                             List<String> with, List<Filter> filters, int offset, int size, int candidateLimit) {
    public DirectoryQuery {
        if (directory == null || directory.isBlank()) throw new IllegalArgumentException("Directory is required");
        with = List.copyOf(with);
        filters = List.copyOf(filters);
        if (offset < 0 || size < 1 || size > 500) throw new IllegalArgumentException("page size must be 1..500 and offset nonnegative");
        if (candidateLimit < 1 || candidateLimit > 100_000) throw new IllegalArgumentException("candidateLimit must be 1..100000");
    }
    public static DirectoryQuery find(String directory) { return new DirectoryQuery(directory, null, null, null, List.of(), List.of(), 0, 50, 1000); }
    public DirectoryQuery eq(String caseName) { return new DirectoryQuery(directory, caseName, from, to, with, filters, offset, size, candidateLimit); }
    public DirectoryQuery from(String value) { return new DirectoryQuery(directory, caseName, value, to, with, filters, offset, size, candidateLimit); }
    public DirectoryQuery to(String value) { return new DirectoryQuery(directory, caseName, from, value, with, filters, offset, size, candidateLimit); }
    public DirectoryQuery with(String path) { List<String> values = new ArrayList<>(with); values.add(path); return new DirectoryQuery(directory, caseName, from, to, values, filters, offset, size, candidateLimit); }
    public DirectoryQuery where(String path, Object value) { return add(new Filter(path, Operator.EQ, value, false)); }
    public DirectoryQuery whereSensitive(String path, Object value) { return add(new Filter(path, Operator.EQ, value, true)); }
    public DirectoryQuery whereIn(String path, List<?> values) { return add(new Filter(path, Operator.IN, List.copyOf(values), false)); }
    private DirectoryQuery add(Filter filter) { List<Filter> values = new ArrayList<>(filters); values.add(filter); return new DirectoryQuery(directory, caseName, from, to, with, values, offset, size, candidateLimit); }
    public DirectoryQuery page(int offset, int size) { return new DirectoryQuery(directory, caseName, from, to, with, filters, offset, size, candidateLimit); }
    public DirectoryQuery candidateLimit(int value) { return new DirectoryQuery(directory, caseName, from, to, with, filters, offset, size, value); }
    public enum Operator { EQ, IN }
    public record Filter(String path, Operator operator, Object value, boolean sensitive) {
        public Filter { if (path == null || !path.matches("[A-Za-z_][A-Za-z0-9_.]*")) throw new IllegalArgumentException("invalid filter path: " + path); }
    }
}
