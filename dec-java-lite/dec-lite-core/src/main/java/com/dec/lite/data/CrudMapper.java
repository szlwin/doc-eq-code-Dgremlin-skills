package com.dec.lite.data;

/** Storage adapter contract shared by generated typed mappers. */
public interface CrudMapper<T> {
    T selectById(Long id);
    void insert(T value);
    void update(T value);
}
