package com.dec.lite.directory;

import com.dec.lite.information.MutationSet;
import com.dec.lite.session.SessionError;
import java.util.List;

public record DirectoryExecutionResult(boolean success, String currentDirectory, String error,
                                       PathPlan plan, MutationSet mutations,
                                       List<ExecutionTraceEvent> trace, SessionError errorDetail) {
    public DirectoryExecutionResult { trace = List.copyOf(trace); }
}
