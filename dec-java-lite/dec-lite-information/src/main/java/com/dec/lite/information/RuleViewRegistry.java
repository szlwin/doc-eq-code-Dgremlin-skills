package com.dec.lite.information;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
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
                             TransactionScope transaction) {
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
    private volatile boolean frozen;

    public synchronized RuleViewRegistry register(String system, String viewRef, String ruleRef, Evaluator evaluator) {
        if (frozen) throw new IllegalStateException("RuleView registry is frozen");
        if (evaluator == null) throw new IllegalArgumentException("RuleView evaluator is required");
        RuleViewKey key = new RuleViewKey(system, viewRef, ruleRef);
        Evaluator previous = evaluators.putIfAbsent(key, evaluator);
        if (previous != null) throw new InformationCompilationException("duplicate RuleView evaluator: " + key);
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
        RuleViewKey match = null;
        for (RuleViewKey key : evaluators.keySet()) {
            if (key.system().equals(system) && key.ruleRef().equals(ruleRef)) {
                if (match != null) return RecognitionResult.error(context.version(), "ambiguous RuleView action: " + system + "." + ruleRef);
                match = key;
            }
        }
        if (match == null) return RecognitionResult.unresolved(context.version(), "no system-scoped RuleView evaluator registered for " + system + "." + ruleRef);
        return invoke(match, context, payload, mutations);
    }

    public RecognitionResult invoke(String system, String ruleRef, ModelContext context,
                                    Map<String, Object> payload, MutationSet mutations,
                                    Map<String, Object> produced) {
        RuleViewKey match = null;
        for (RuleViewKey key : evaluators.keySet()) {
            if (key.system().equals(system) && key.ruleRef().equals(ruleRef)) {
                if (match != null) return RecognitionResult.error(context.version(), "ambiguous RuleView action: " + system + "." + ruleRef);
                match = key;
            }
        }
        if (match == null) return RecognitionResult.unresolved(context.version(), "no system-scoped RuleView evaluator registered for " + system + "." + ruleRef);
        return invoke(match, context, payload, mutations, null, produced, null);
    }

    public RecognitionResult invoke(String system, String ruleRef, ModelContext context,
                                    Map<String, Object> payload, MutationSet mutations,
                                    Map<String, Object> produced, TransactionScope transaction) {
        RuleViewKey match = null;
        for (RuleViewKey key : evaluators.keySet()) {
            if (key.system().equals(system) && key.ruleRef().equals(ruleRef)) {
                if (match != null) return RecognitionResult.error(context.version(), "ambiguous RuleView action: " + system + "." + ruleRef);
                match = key;
            }
        }
        if (match == null) return RecognitionResult.unresolved(context.version(), "no system-scoped RuleView evaluator registered for " + system + "." + ruleRef);
        return invoke(match, context, payload, mutations, null, produced, transaction);
    }

    private RecognitionResult invoke(RuleViewKey key, ModelContext context, Map<String, Object> payload,
                                     MutationSet mutations, InformationDefinition information,
                                     Map<String, Object> produced, TransactionScope transaction) {
        Evaluator evaluator = evaluators.get(key);
        if (evaluator == null) return RecognitionResult.unresolved(context.version(), "no system-scoped RuleView evaluator registered for " + key);
        try {
            RecognitionResult result = evaluator.evaluate(new Invocation(key, information, context, payload, mutations, produced, transaction));
            return result == null ? RecognitionResult.error(context.version(), "RuleView evaluator returned null for " + key) : result;
        } catch (SecurityException exception) {
            return RecognitionResult.error(context.version(), "ACCESS_DENIED " + key + ": " + exception.getMessage());
        } catch (RuntimeException exception) {
            return RecognitionResult.error(context.version(), "RuleView evaluator failed for " + key + ": " + exception.getMessage());
        }
    }
}
