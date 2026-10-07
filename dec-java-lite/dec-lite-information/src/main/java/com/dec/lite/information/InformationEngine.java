package com.dec.lite.information;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;

/** P3 Information evaluation, materialization and reverse-DAG invalidation API. */
public final class InformationEngine {
    private final InformationEngineContext runtime;
    private final Map<ModelContext, Map<InformationKey, Cached>> cache = java.util.Collections.synchronizedMap(new IdentityHashMap<>());

    public InformationEngine(InformationCompilation compilation) {
        this(new InformationEngineContext(compilation, new RuleViewRegistry()));
    }

    public InformationEngine(InformationEngineContext runtime) { this.runtime = runtime; }
    public InformationCompilation compilation() { return runtime.compilation(); }
    public InformationEngineContext context() { return runtime; }

    public synchronized RecognitionResult evaluate(InformationKey key, ModelContext context) {
        if (!compilation().definitions().containsKey(key)) return RecognitionResult.error(context.version(), "unknown Information: " + key);
        Map<InformationKey, Cached> contextCache = cache.computeIfAbsent(context, ignored -> new HashMap<>());
        Cached cached = contextCache.get(key);
        if (cached != null && cached.version().equals(context.version())) return cached.result();
        RecognitionResult result = evaluateUncached(key, context, new HashSet<>());
        contextCache.put(key, new Cached(context.version(), result));
        return result;
    }

    public synchronized Set<InformationKey> invalidate(MutationSet mutations) {
        Set<InformationKey> impacted = new LinkedHashSet<>();
        for (InformationKey key : compilation().modelExpressions().keySet()) if (mutations.touchesAny(compilation().modelExpressions().get(key).readPaths())) impacted.add(key);
        // RuleView evaluators currently do not publish a static read-path contract.
        // Invalidate them conservatively, then propagate through the reverse DAG.
        if (!mutations.isEmpty() && runtime.ruleViews().hasAnyEvaluator()) {
            compilation().definitions().values().stream()
                    .filter(definition -> definition.kind() == InformationKind.RULE_VIEW_ATOMIC)
                    .map(InformationDefinition::key)
                    .forEach(impacted::add);
        }
        Set<InformationKey> queue = new LinkedHashSet<>(impacted);
        while (!queue.isEmpty()) { InformationKey key = queue.iterator().next(); queue.remove(key); for (InformationKey dependent : compilation().reverseDependencies().getOrDefault(key, Set.of())) if (impacted.add(dependent)) queue.add(dependent); }
        for (Map<InformationKey, Cached> values : cache.values()) impacted.forEach(values::remove);
        return Set.copyOf(impacted);
    }

    public synchronized MutationSet materialize(InformationKey key, ModelContext context) {
        InformationDefinition definition = compilation().definitions().get(key);
        if (definition == null) throw new InformationCompilationException("unknown Information: " + key);
        if (!definition.materializable()) throw new InformationCompilationException("only atomic ruleData Information with changeData is materializable: " + key);
        ModelContext candidate = context.copy(); MutationSet mutations = new MutationSet(); applyChanges(definition.changeData(), candidate, mutations);
        cache.values().forEach(values -> values.remove(key));
        RecognitionResult result = evaluate(key, candidate);
        if (result.status() != RecognitionResult.Status.TRUE) throw new InformationCompilationException("materialization did not establish " + key + ": " + result.status());
        context.replaceFrom(candidate); invalidate(mutations); return mutations;
    }

    public InformationExplanation explain(InformationKey key) {
        InformationDefinition definition = compilation().definitions().get(key);
        if (definition == null) throw new InformationCompilationException("unknown Information: " + key);
        return new InformationExplanation(key, definition.kind(), compilation().dependencies().getOrDefault(key, Set.of()), compilation().modelExpressions().get(key) == null ? Set.of() : compilation().modelExpressions().get(key).readPaths(), compilation().graphDigest());
    }

    private RecognitionResult evaluateUncached(InformationKey key, ModelContext context, Set<InformationKey> visiting) {
        if (!visiting.add(key)) return RecognitionResult.error(context.version(), "Information cycle while evaluating: " + key);
        InformationDefinition definition = compilation().definitions().get(key);
        RecognitionResult result;
        if (definition.kind() == InformationKind.MODEL_EXPRESSION_ATOMIC) result = compilation().modelExpressions().get(key).evaluate(context);
        else if (definition.kind() == InformationKind.RULE_VIEW_ATOMIC) {
            result = runtime.ruleViews().invoke(definition, context);
        } else {
            InformationExpression expression = compilation().informationExpressions().get(key);
            result = expression.evaluate(dependency -> evaluateUncached(dependency, context, visiting), context.version());
        }
        visiting.remove(key);
        return withDependency(key, result).withTrace(new TraceEvent(key, definition.kind().name(), result.status(), definition.designId()));
    }

    private RecognitionResult withDependency(InformationKey key, RecognitionResult result) {
        return new RecognitionResult(result.status(), result.evidence(), compilation().dependencies().getOrDefault(key, Set.of()).stream().sorted().toList(), result.errors(), result.modelVersion(), result.trace());
    }

    private void invalidateAll() { cache.clear(); }

    private static void applyChanges(String source, ModelContext context, MutationSet mutations) {
        for (String statement : source.split(";")) {
            String value = statement.trim(); if (value.isEmpty()) continue;
            if (value.startsWith("every(") && value.endsWith(")")) { applyEvery(value.substring(6, value.length() - 1), context, mutations); continue; }
            int separator = value.indexOf(':'); if (separator <= 0) throw new InformationCompilationException("invalid changeData statement: " + value);
            context.write(value.substring(0, separator).trim(), literal(value.substring(separator + 1).trim()), mutations);
        }
    }
    private static void applyEvery(String source, ModelContext context, MutationSet mutations) {
        int comma = source.indexOf(','); if (comma <= 0) throw new InformationCompilationException("invalid every changeData statement: " + source);
        String listPath = source.substring(0, comma).trim(); String assignment = source.substring(comma + 1).trim(); int nested = assignment.indexOf(':');
        if (nested <= 0) throw new InformationCompilationException("every changeData requires path:value: " + source);
        Object raw = context.read(listPath); if (!(raw instanceof List<?> list)) throw new InformationCompilationException("every changeData path is not a list: " + listPath);
        String childPath = assignment.substring(0, nested).trim(); Object newValue = literal(assignment.substring(nested + 1).trim());
        for (int index = 0; index < list.size(); index++) {
            Object item = list.get(index); if (!(item instanceof Map<?, ?> map)) throw new InformationCompilationException("every changeData item is not a map: " + listPath);
            @SuppressWarnings("unchecked") Map<String, Object> mutable = (Map<String, Object>) map; String path = listPath + "[" + index + "]." + childPath; context.checkWrite(path); Object old = mutable.put(childPath, newValue); mutations.add(new Mutation(path, old, newValue));
        }
    }
    private static Object literal(String value) { if (value.equalsIgnoreCase("null")) return null; if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return Boolean.valueOf(value); try { if (value.contains(".")) return Double.valueOf(value); return Long.valueOf(value); } catch (NumberFormatException ignored) { if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) return value.substring(1, value.length() - 1); throw new InformationCompilationException("invalid changeData literal: " + value); } }
    private record Cached(String version, RecognitionResult result) { }
}
