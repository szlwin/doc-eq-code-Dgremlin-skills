package com.dec.lite.query;

import com.dec.lite.information.InformationEngine;
import com.dec.lite.information.InformationKey;
import com.dec.lite.information.ModelContext;
import com.dec.lite.information.RecognitionResult;
import com.dec.lite.session.ExecutionSession;
import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.runtime.RuntimeFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bounded two-phase query: page root IDs, load complete joins, then classify Information. */
public final class QueryRunner {
    private final QueryExecutor executor;
    private final SqlTranslator translator;
    private final InformationEngine information;
    private final ExecutionSession session;
    private final ResultAssembler assembler = new ResultAssembler();
    public QueryRunner(QueryExecutor executor, SqlTranslator translator, InformationEngine information) {
        this.executor = executor; this.translator = translator; this.information = information; this.session = null;
    }
    public QueryRunner(QueryExecutor executor, SqlTranslator translator, ExecutionSession session) {
        this.executor = executor; this.translator = translator; this.information = session.information(); this.session = session;
    }
    public QueryResult run(QueryPlan plan) {
        if (session != null) session.trace("QUERY_STARTED", plan.directory(), "route=" + plan.route());
        try { return runInternal(plan); }
        catch (RuntimeException failure) {
            if (session != null) {
                RuntimeErrorCode code = failure instanceof RuntimeFailure typed ? typed.code() : RuntimeErrorCode.QUERY;
                String key = failure instanceof RuntimeFailure typed && typed.entityKey() != null ? typed.entityKey() : plan.directory();
                session.recordError(code, key, failure instanceof RuntimeFailure typed ? typed.source() : null,
                        failure.getMessage(), failure);
            }
            throw failure;
        }
    }
    private QueryResult runInternal(QueryPlan plan) {
        int maximum = plan.runtimeOnly() ? plan.candidateLimit() : Math.max(plan.candidateLimit(), plan.offset() + plan.size());
        int scanned = 0; int selected = 0; List<ModelContext> result = new ArrayList<>();
        List<String> trace = new ArrayList<>(plan.trace()); trace.add("candidateLimit=" + maximum);
        boolean exhausted = false;
        while (result.size() < plan.size() && scanned < maximum) {
            int batchSize = Math.min(100, maximum - scanned);
            SqlStatement candidateSql = translator.candidates(plan, scanned, batchSize);
            List<Map<String, Object>> candidates = executor.query(candidateSql, plan.route());
            if (candidates.size() > batchSize) throw new QueryExecutionException("QueryExecutor returned more candidate IDs than requested");
            if (candidates.isEmpty()) { exhausted = true; break; }
            List<Object> ids = new ArrayList<>();
            for (Map<String, Object> candidate : candidates) {
                Object id = candidate.get(plan.assembly().rootIdAlias()); if (id == null) throw new QueryExecutionException("candidate row has no root ID");
                ids.add(id);
            }
            Map<String, ModelContext> models = assembler.assemble(plan, executor.query(translator.details(plan, ids), plan.route()));
            for (Object id : ids) {
                ModelContext model = models.get(String.valueOf(id));
                if (model == null) throw new QueryExecutionException("detail query omitted candidate ID " + id);
                if (matches(plan, model)) {
                    if (selected++ >= plan.offset() && result.size() < plan.size()) result.add(model);
                }
            }
            scanned += candidates.size();
            trace.add("scanned=" + scanned + " selected=" + selected);
            if (candidates.size() < batchSize) { exhausted = true; break; }
        }
        if (!exhausted && scanned >= maximum && result.size() < plan.size())
            throw new QueryExecutionException("candidateLimit reached before page completion: " + maximum);
        trace.add("postFilter=" + plan.runtimeOnly() + " returned=" + result.size());
        if (session != null) session.trace("QUERY_COMPLETED", plan.directory(), "scanned=" + scanned + " returned=" + result.size());
        return new QueryResult(result, scanned, trace);
    }
    private boolean matches(QueryPlan plan, ModelContext model) {
        int matches = 0;
        for (QueryPlan.CaseBranch branch : plan.branches()) {
            boolean accepted = true;
            for (InformationKey key : branch.information()) {
                RecognitionResult result = information.evaluate(key, model);
                if (result.status() == RecognitionResult.Status.ERROR || result.status() == RecognitionResult.Status.UNRESOLVED)
                    throw new QueryExecutionException("Information query is " + result.status() + ": " + key + " " + result.errors());
                if (result.status() != RecognitionResult.Status.TRUE) { accepted = false; break; }
            }
            if (accepted) matches++;
        }
        if (matches > 1) throw new QueryExecutionException("result case is ambiguous for model");
        return matches == 1;
    }
}
