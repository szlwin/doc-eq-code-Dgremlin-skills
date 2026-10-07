package com.dec.lite.information;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/** Strict Information-only boolean AST. */
public final class InformationExpression {
    public enum Kind { REFERENCE, AND, OR, NOT }
    private final Kind kind;
    private final InformationKey reference;
    private final InformationExpression left;
    private final InformationExpression right;

    private InformationExpression(Kind kind, InformationKey reference, InformationExpression left, InformationExpression right) {
        this.kind = kind; this.reference = reference; this.left = left; this.right = right;
    }
    public static InformationExpression reference(InformationKey key) { return new InformationExpression(Kind.REFERENCE, key, null, null); }
    public static InformationExpression and(InformationExpression left, InformationExpression right) { return new InformationExpression(Kind.AND, null, left, right); }
    public static InformationExpression or(InformationExpression left, InformationExpression right) { return new InformationExpression(Kind.OR, null, left, right); }
    public static InformationExpression not(InformationExpression child) { return new InformationExpression(Kind.NOT, null, child, null); }
    public Kind kind() { return kind; }
    public InformationKey reference() { return reference; }
    public InformationExpression left() { return left; }
    public InformationExpression right() { return right; }
    public Set<InformationKey> dependencies() { Set<InformationKey> result = new TreeSet<>(); collect(result); return java.util.Collections.unmodifiableSet(result); }
    private void collect(Set<InformationKey> result) { if (kind == Kind.REFERENCE) result.add(reference); else { left.collect(result); if (right != null) right.collect(result); } }
    public String canonical() { return switch (kind) { case REFERENCE -> "ref(" + reference + ")"; case NOT -> "not(" + left.canonical() + ")"; case AND -> "and(" + left.canonical() + "," + right.canonical() + ")"; case OR -> "or(" + left.canonical() + "," + right.canonical() + ")"; }; }
    public RecognitionResult evaluate(Function<InformationKey, RecognitionResult> resolver, String version) {
        return switch (kind) {
            case REFERENCE -> resolver.apply(reference);
            case NOT -> invert(left.evaluate(resolver, version), version);
            case AND -> evaluateAnd(resolver, version);
            case OR -> evaluateOr(resolver, version);
        };
    }
    private RecognitionResult evaluateAnd(Function<InformationKey, RecognitionResult> resolver, String version) {
        RecognitionResult first = left.evaluate(resolver, version);
        if (first.status() == RecognitionResult.Status.ERROR || first.status() == RecognitionResult.Status.FALSE) return first;
        RecognitionResult second = right.evaluate(resolver, version);
        return combine("and", first, second, version);
    }
    private RecognitionResult evaluateOr(Function<InformationKey, RecognitionResult> resolver, String version) {
        RecognitionResult first = left.evaluate(resolver, version);
        if (first.status() == RecognitionResult.Status.ERROR || first.status() == RecognitionResult.Status.TRUE) return first;
        RecognitionResult second = right.evaluate(resolver, version);
        return combine("or", first, second, version);
    }
    private static RecognitionResult invert(RecognitionResult value, String version) { return new RecognitionResult(value.status() == RecognitionResult.Status.TRUE ? RecognitionResult.Status.FALSE : value.status() == RecognitionResult.Status.FALSE ? RecognitionResult.Status.TRUE : value.status(), value.evidence(), value.dependencies(), value.errors(), version, value.trace()); }
    private static RecognitionResult combine(String op, RecognitionResult left, RecognitionResult right, String version) {
        if (left.status() == RecognitionResult.Status.ERROR) return merge(left, right, version, RecognitionResult.Status.ERROR);
        if (right.status() == RecognitionResult.Status.ERROR) return merge(left, right, version, RecognitionResult.Status.ERROR);
        if ("and".equals(op)) {
            if (left.status() == RecognitionResult.Status.FALSE || right.status() == RecognitionResult.Status.FALSE) return merge(left, right, version, RecognitionResult.Status.FALSE);
            if (left.status() == RecognitionResult.Status.UNRESOLVED || right.status() == RecognitionResult.Status.UNRESOLVED) return merge(left, right, version, RecognitionResult.Status.UNRESOLVED);
            return merge(left, right, version, RecognitionResult.Status.TRUE);
        }
        if (left.status() == RecognitionResult.Status.TRUE || right.status() == RecognitionResult.Status.TRUE) return merge(left, right, version, RecognitionResult.Status.TRUE);
        if (left.status() == RecognitionResult.Status.UNRESOLVED || right.status() == RecognitionResult.Status.UNRESOLVED) return merge(left, right, version, RecognitionResult.Status.UNRESOLVED);
        return merge(left, right, version, RecognitionResult.Status.FALSE);
    }
    private static RecognitionResult merge(RecognitionResult left, RecognitionResult right, String version, RecognitionResult.Status status) {
        java.util.LinkedHashSet<String> evidence = new java.util.LinkedHashSet<>(left.evidence()); evidence.addAll(right.evidence());
        java.util.LinkedHashSet<String> errors = new java.util.LinkedHashSet<>(left.errors()); errors.addAll(right.errors());
        java.util.ArrayList<TraceEvent> trace = new java.util.ArrayList<>(left.trace()); trace.addAll(right.trace());
        java.util.LinkedHashSet<InformationKey> dependencies = new java.util.LinkedHashSet<>(left.dependencies()); dependencies.addAll(right.dependencies());
        return new RecognitionResult(status, evidence.stream().toList(), dependencies.stream().toList(), errors.stream().toList(), version, trace);
    }
}
