package com.dec.lite.information;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System-scoped RuleView dispatch. A bare rule name is never sufficient to
 * select an evaluator; the owning system and view are part of the key.
 */
public final class RuleViewRegistry {
    @FunctionalInterface
    public interface Evaluator {
        RecognitionResult evaluate(Invocation invocation);
    }

    public record Invocation(RuleViewKey key, InformationDefinition information, ModelContext context,
                             Map<String, Object> payload, MutationSet mutations, Map<String, Object> produced,
                             TransactionScope transaction, String dataSourceOverride, String defaultDataSource) {
        public Invocation(RuleViewKey key, InformationDefinition information, ModelContext context,
                          Map<String, Object> payload, MutationSet mutations, Map<String, Object> produced,
                          TransactionScope transaction) {
            this(key, information, context, payload, mutations, produced, transaction, null, null);
        }
        public String resolveDataSource(String ruleSource) {
            return dataSourceOverride != null ? dataSourceOverride : ruleSource != null ? ruleSource : defaultDataSource;
        }
        public Invocation(RuleViewKey key, InformationDefinition information, ModelContext context) {
            this(key, information, context, Map.of(), new MutationSet(), new LinkedHashMap<>(), new TransactionScope() { });
        }
        public Invocation {
            payload = Map.copyOf(payload == null ? Map.of() : new LinkedHashMap<>(payload));
            mutations = mutations == null ? new MutationSet() : mutations;
            produced = produced == null ? new LinkedHashMap<>() : produced;
            transaction = transaction == null ? new TransactionScope() { } : transaction;
        }
        public Object write(String path, Object value) {
            if (information != null) throw new IllegalStateException("Information recognition cannot write model: " + key);
            return context.write(path, value, mutations);
        }
        public void produce(String ref, Object value) { produced.put(ref, value); }
    }

    private final Map<RuleViewKey, Evaluator> evaluators = new ConcurrentHashMap<>();
    private record RuleReference(String system, String ruleRef) { }
    private record Resolution(RuleViewKey key, boolean ambiguous) { }

    // Built during registration; multiple views for the same system/rule remain ambiguous.
    private final Map<RuleReference, Resolution> resolutions = new ConcurrentHashMap<>();
    private volatile boolean frozen;
    private final String defaultDataSource;
    public RuleViewRegistry() { this(null); }
    public RuleViewRegistry(String defaultDataSource) { this.defaultDataSource = defaultDataSource; }

    public synchronized RuleViewRegistry register(String system, String viewRef, String ruleRef, Evaluator evaluator) {
        if (frozen) throw new IllegalStateException("RuleView registry is frozen");
        if (evaluator == null) throw new IllegalArgumentException("RuleView evaluator is required");
        RuleViewKey key = new RuleViewKey(system, viewRef, ruleRef);
        Evaluator previous = evaluators.putIfAbsent(key, evaluator);
        if (previous != null) throw new InformationCompilationException("duplicate RuleView evaluator: " + key);
        resolutions.compute(new RuleReference(system, ruleRef), (ignored, previousResolution) ->
                previousResolution == null ? new Resolution(key, false) : new Resolution(null, true));
        return this;
    }
    public synchronized RuleViewRegistry freeze() { frozen = true; return this; }

    public boolean contains(String system, String viewRef, String ruleRef) {
        return evaluators.containsKey(new RuleViewKey(system, viewRef, ruleRef));
    }

    /** Conservative invalidation boundary when an evaluator does not publish read paths. */
    public boolean hasAnyEvaluator() { return !evaluators.isEmpty(); }

    public RecognitionResult invoke(InformationDefinition information, ModelContext context) {
        RuleViewKey key = new RuleViewKey(information.key().system(), information.viewRef(), information.ruleRef());
        ModelContext snapshot = context.copy();
        RecognitionResult result = invoke(key, snapshot, Map.of(), new MutationSet(), information, new LinkedHashMap<>(), null);
        if (!snapshot.values().equals(context.values())) return RecognitionResult.error(context.version(), "RuleView recognition mutated model: " + key);
        return result;
    }

    public RecognitionResult invoke(RuleViewKey key, ModelContext context, Map<String, Object> payload, MutationSet mutations) {
        return invoke(key, context, payload, mutations, null, new LinkedHashMap<>(), null);
    }

    /** Resolve an action's system + ruleRef only when that pair is unambiguous. */
    public RecognitionResult invoke(String system, String ruleRef, ModelContext context,
                                    Map<String, Object> payload, MutationSet mutations) {
        return invoke(system, ruleRef, context, payload, mutations, new LinkedHashMap<>(), null);
    }

    public RecognitionResult invoke(String system, String ruleRef, ModelContext context,
                                    Map<String, Object> payload, MutationSet mutations,
                                    Map<String, Object> produced) {
        return invoke(system, ruleRef, context, payload, mutations, produced, null);
    }

    public RecognitionResult invoke(String system, String ruleRef, ModelContext context,
                                    Map<String, Object> payload, MutationSet mutations,
                                    Map<String, Object> produced, TransactionScope transaction) {
        return invoke(system, ruleRef, context, payload, mutations, produced, transaction, null, defaultDataSource);
    }

    public RecognitionResult invoke(String system, String ruleRef, ModelContext context,
                                    Map<String, Object> payload, MutationSet mutations,
                                    Map<String, Object> produced, TransactionScope transaction,
                                    String dataSourceOverride, String businessDataSource) {
        Resolution resolution = resolve(system, ruleRef);
        if (resolution == null) return RecognitionResult.unresolved(context.version(), "no system-scoped RuleView evaluator registered for " + system + "." + ruleRef);
        if (resolution.ambiguous()) return RecognitionResult.error(context.version(), "ambiguous RuleView action: " + system + "." + ruleRef);
        return invoke(resolution.key(), context, payload, mutations, null, produced, transaction, dataSourceOverride, businessDataSource);
    }

    private Resolution resolve(String system, String ruleRef) {
        return resolutions.get(new RuleReference(system, ruleRef));
    }

    private RecognitionResult invoke(RuleViewKey key, ModelContext context, Map<String, Object> payload,
                                     MutationSet mutations, InformationDefinition information,
                                     Map<String, Object> produced, TransactionScope transaction) {
        return invoke(key, context, payload, mutations, information, produced, transaction, null, defaultDataSource);
    }

    private RecognitionResult invoke(RuleViewKey key, ModelContext context, Map<String, Object> payload,
                                     MutationSet mutations, InformationDefinition information,
                                     Map<String, Object> produced, TransactionScope transaction,
                                     String dataSourceOverride, String businessDataSource) {
        Evaluator evaluator = evaluators.get(key);
        if (evaluator == null) return RecognitionResult.unresolved(context.version(), "no system-scoped RuleView evaluator registered for " + key);
        try {
            RecognitionResult result = evaluator.evaluate(new Invocation(key, information, context, payload, mutations, produced, transaction, dataSourceOverride, businessDataSource));
            return result == null ? RecognitionResult.error(context.version(), "RuleView evaluator returned null for " + key) : result;
        } catch (SecurityException exception) {
            return RecognitionResult.error(context.version(), "ACCESS_DENIED " + key + ": " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            return RecognitionResult.error(context.version(), "RuleView evaluator failed for " + key + ": " + exception.getMessage(), exception);
        }
    }
}
