package com.dec.lite.directory;

/** Framework failure for a Directory workflow; application layers may map it to their transport error model. */
public final class DirectoryWorkflowException extends RuntimeException {
    private final DirectoryExecutionResult execution;

    public DirectoryWorkflowException(String target, DirectoryExecutionResult execution) {
        super("DEC workflow " + target + " failed: " + execution.error(),
                execution.errorDetail() == null ? null : execution.errorDetail().cause());
        this.execution = execution;
    }

    public DirectoryExecutionResult execution() { return execution; }
}
