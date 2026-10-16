package com.dec.lite.data;

import java.util.Objects;

/** Shared typed DAO behavior; generated DAOs retain only the design-specific type and mapper binding. */
public class CrudDao<T> {
    private final CrudMapper<T> mapper;

    protected CrudDao(CrudMapper<T> mapper) { this.mapper = Objects.requireNonNull(mapper, "mapper"); }

    public T find(Long id) { return mapper.selectById(id); }
    public void insert(T value) { mapper.insert(value); }
    public void update(T value) { mapper.update(value); }
}
