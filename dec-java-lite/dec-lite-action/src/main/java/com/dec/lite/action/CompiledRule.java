package com.dec.lite.action;

public record CompiledRule(String id, String name, String type, String property, String pattern,
                           String process, String dataSource) { }
