package com.dec.lite.query;

import com.dec.lite.action.*;
import com.dec.lite.directory.DirectoryGraph;
import com.dec.lite.information.*;
import com.dec.lite.model.DecProject;
import com.dec.lite.session.ExecutionSession;
import com.dec.lite.session.TransactionPolicy;

import java.time.Instant;
import java.util.Map;

/** Immutable published compilation; each opened Session owns a fresh model and cache. */
public record RuntimeSnapshot(DecProject project, InformationCompilation information,
                              DirectoryGraph directories, RuleViewRegistry ruleViews,
                              CustomActionRegistry customActions, QueryCompiler queries,
                              SystemAccessPolicy accessPolicy, String digest) {
    public ExecutionSession openSession(ModelContext model, Map<String, Object> payload,
                                        Instant deadline, String currentDirectory, TransactionPolicy policy) {
        return new ExecutionSession(new InformationEngine(new InformationEngineContext(information, ruleViews)),
                new ActionRuntime(ruleViews, customActions), model, payload, deadline, currentDirectory,
                policy, null, accessPolicy, digest);
    }
}
