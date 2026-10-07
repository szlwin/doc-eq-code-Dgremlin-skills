package com.dec.lite.directory;

public record ExecutionTraceEvent(String directory, DirectoryStage stage, String detail) {
    public ExecutionTraceEvent {
        if (directory == null || directory.isBlank()) throw new IllegalArgumentException("trace directory is required");
        if (stage == null) throw new IllegalArgumentException("trace stage is required");
    }
}
