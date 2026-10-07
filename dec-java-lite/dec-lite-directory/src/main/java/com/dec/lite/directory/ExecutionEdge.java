package com.dec.lite.directory;

/** Ordinary execution progresses from child to parent. */
public record ExecutionEdge(String child, String parent) { }
