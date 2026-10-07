package com.dec.lite.query;

public record JoinPlan(String path, String alias, String parentAlias, String table,
                       String parentColumn, String childColumn, boolean many, String idColumn) { }
