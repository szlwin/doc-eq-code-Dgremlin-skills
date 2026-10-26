package com.dec.lite.information;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Compiler for the model-only ruleData language. It never accepts Information keys. */
public final class ModelExpressionCompiler {
    public CompiledExpression compile(String source) {
        if (source == null || source.isBlank()) throw new InformationCompilationException("model expression is empty");
        Parser parser = new Parser(source);
        Node node = parser.parseOr();
        parser.expectEnd();
        return new CompiledExpression(node, node.readPaths(), node.canonical());
    }

    public record CompiledExpression(Node node, Set<String> readPaths, String canonical) {
        public RecognitionResult evaluate(ModelContext context) { return node.evaluate(context.values(), context.version()); }
        /** Necessary equalities from the parsed AST; never extract conditions from OR/NOT/quantifiers. */
        public EqualityProjection necessaryEqualities() { return equalities(node); }
    }

    public record Equality(String path, Object value) { }
    public record EqualityProjection(List<Equality> conditions, boolean complete) {
        public EqualityProjection { conditions = List.copyOf(conditions); }
    }
    private static EqualityProjection equalities(Node node) {
        if (node instanceof Compare compare && "=".equals(compare.op()))
            return new EqualityProjection(List.of(new Equality(compare.path(), compare.literal().value())), true);
        if (node instanceof Binary binary && "and".equals(binary.op())) {
            var left = equalities(binary.left()); var right = equalities(binary.right());
            var conditions = new ArrayList<>(left.conditions()); conditions.addAll(right.conditions());
            return new EqualityProjection(conditions, left.complete() && right.complete());
        }
        return new EqualityProjection(List.of(), false);
    }

    interface Node {
        RecognitionResult evaluate(Object scope, String version);
        Set<String> readPaths();
        String canonical();
    }

    private record Literal(Object value, String source) { }

    private static final class Parser {
        private final List<String> tokens = new ArrayList<>();
        private int index;
        Parser(String source) {
            StringBuilder current = new StringBuilder(); boolean quoted = false; char quote = 0;
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (quoted) { current.append(c); if (c == quote && (i == 0 || source.charAt(i - 1) != '\\')) quoted = false; continue; }
                if (c == '\'' || c == '"') { quoted = true; quote = c; current.append(c); continue; }
                if (Character.isWhitespace(c)) { flush(current); continue; }
                if (c == '(' || c == ')' || c == ',') { flush(current); tokens.add(String.valueOf(c)); continue; }
                if (c == '=' || c == '!' || c == '<' || c == '>') {
                    flush(current);
                    if (c != '=' && i + 1 < source.length() && source.charAt(i + 1) == '=') {
                        tokens.add("" + c + '='); i++;
                    } else tokens.add(String.valueOf(c));
                    continue;
                }
                current.append(c);
            }
            if (quoted) throw new InformationCompilationException("unterminated model expression string"); flush(current);
        }
        private void flush(StringBuilder current) { if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); } }
        Node parseOr() { Node left = parseAnd(); while (accept("or")) left = new Binary("or", left, parseAnd()); return left; }
        Node parseAnd() { Node left = parseUnary(); while (accept("and")) left = new Binary("and", left, parseUnary()); return left; }
        Node parseUnary() { if (accept("not")) return new Unary(parseUnary()); return parsePrimary(); }
        Node parsePrimary() {
            if (accept("(")) { Node value = parseOr(); require(")"); return value; }
            String first = next();
            if (accept("(")) {
                if (!first.equals("every") && !first.equals("any") && !first.equals("exists")) throw error("unsupported model function " + first);
                String path = next();
                if (first.equals("exists")) { require(")"); return new Exists(path); }
                require(","); Node condition = parseOr(); require(")"); return new Quantifier(first, path, condition);
            }
            if (peek("=") || peek("!=") || peek("!") || peek("<") || peek("<=") || peek(">") || peek(">=")) { String op = next(); if ("!".equals(op)) op = "!="; return new Compare(path(first), op, literal(next())); }
            throw error("model expression requires comparison or supported function: " + first);
        }
        String next() { if (index >= tokens.size()) throw error("unexpected end"); return tokens.get(index++); }
        boolean peek(String token) { return index < tokens.size() && token.equals(tokens.get(index)); }
        boolean accept(String token) { if (peek(token)) { index++; return true; } return false; }
        void require(String token) { if (!accept(token)) throw error("expected " + token); }
        void expectEnd() { if (index != tokens.size()) throw error("unexpected token " + tokens.get(index)); }
        String path(String value) { if (!value.matches("[A-Za-z_][A-Za-z0-9_.]*")) throw error("model path required"); return value; }
        Literal literal(String value) {
            if (value.equalsIgnoreCase("null")) return new Literal(null, "null");
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return new Literal(Boolean.valueOf(value), value.toLowerCase(Locale.ROOT));
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) return new Literal(value.substring(1, value.length() - 1), value);
            try { return new Literal(new java.math.BigDecimal(value), value); }
            catch (NumberFormatException ignored) { throw error("literal required: " + value); }
        }
        InformationCompilationException error(String message) { return new InformationCompilationException(message); }
    }

    private record TruthPath(String path) implements Node {
        public RecognitionResult evaluate(Object scope, String version) {
            Lookup lookup = lookup(scope, path);
            if (!lookup.present()) return RecognitionResult.unresolved(version, "missing model path: " + path);
            if (!(lookup.value() instanceof Boolean value)) return RecognitionResult.error(version, "model path is not boolean: " + path);
            return RecognitionResult.of(value ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, version, path);
        }
        public Set<String> readPaths() { return Set.of(path); }
        public String canonical() { return "truth(" + path + ")"; }
    }
    private record Exists(String path) implements Node {
        public RecognitionResult evaluate(Object scope, String version) { Lookup value = lookup(scope, path); return RecognitionResult.of(value.present() && value.value() != null ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, version, path); }
        public Set<String> readPaths() { return Set.of(path); }
        public String canonical() { return "exists(" + path + ")"; }
    }
    private record Compare(String path, String op, Literal literal) implements Node {
        public RecognitionResult evaluate(Object scope, String version) {
            Lookup lookup = lookup(scope, path);
            if (!lookup.present()) return RecognitionResult.unresolved(version, "missing model path: " + path);
            Object actual = lookup.value(), expected = literal.value();
            boolean matches;
            try {
                if ("=".equals(op) || "!=".equals(op)) {
                    boolean equal = com.dec.lite.data.DecValueEquality.equal(actual, expected);
                    matches = "=".equals(op) ? equal : !equal;
                } else {
                    if (!(actual instanceof Number a) || !(expected instanceof Number b))
                        return RecognitionResult.error(version, "numeric comparison requires numbers: " + path);
                    int comparison = decimal(a).compareTo(decimal(b));
                    matches = switch (op) {
                        case "<" -> comparison < 0;
                        case "<=" -> comparison <= 0;
                        case ">" -> comparison > 0;
                        case ">=" -> comparison >= 0;
                        default -> throw new IllegalStateException("unsupported comparison: " + op);
                    };
                }
            } catch (NumberFormatException failure) {
                return RecognitionResult.error(version, "non-finite or invalid numeric value: " + path);
            }
            return RecognitionResult.of(matches ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, version, path);
        }
        public Set<String> readPaths() { return Set.of(path); }
        public String canonical() { return "compare(" + path + op + literal.source() + ")"; }
    }
    private record Unary(Node child) implements Node {
        public RecognitionResult evaluate(Object scope, String version) { RecognitionResult result = child.evaluate(scope, version); return new RecognitionResult(result.status() == RecognitionResult.Status.TRUE ? RecognitionResult.Status.FALSE : result.status() == RecognitionResult.Status.FALSE ? RecognitionResult.Status.TRUE : result.status(), result.evidence(), result.dependencies(), result.errors(), version, result.trace(), result.failure()); }
        public Set<String> readPaths() { return child.readPaths(); }
        public String canonical() { return "not(" + child.canonical() + ")"; }
    }
    private record Binary(String op, Node left, Node right) implements Node {
        public RecognitionResult evaluate(Object scope, String version) {
            RecognitionResult first = left.evaluate(scope, version);
            if ("and".equals(op) && (first.status() == RecognitionResult.Status.ERROR || first.status() == RecognitionResult.Status.FALSE)) return first;
            if ("or".equals(op) && (first.status() == RecognitionResult.Status.ERROR || first.status() == RecognitionResult.Status.TRUE)) return first;
            return combine(op, first, right.evaluate(scope, version), version);
        }
        public Set<String> readPaths() { Set<String> result = new LinkedHashSet<>(left.readPaths()); result.addAll(right.readPaths()); return Set.copyOf(result); }
        public String canonical() { return op + "(" + left.canonical() + "," + right.canonical() + ")"; }
    }
    private record Quantifier(String op, String path, Node condition) implements Node {
        public RecognitionResult evaluate(Object scope, String version) {
            Lookup lookup = lookup(scope, path);
            if (!lookup.present()) return RecognitionResult.unresolved(version, "missing model path: " + path);
            if (!(lookup.value() instanceof List<?> list)) return RecognitionResult.error(version, "quantifier path is not a list: " + path);
            if (list.isEmpty()) return RecognitionResult.of(op.equals("every") ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, version, path);
            boolean unresolved = false;
            for (Object item : list) {
                RecognitionResult result = condition.evaluate(item, version);
                if (result.status() == RecognitionResult.Status.ERROR) return result;
                if (op.equals("every") && result.status() == RecognitionResult.Status.FALSE) return result;
                if (op.equals("any") && result.status() == RecognitionResult.Status.TRUE) return result;
                unresolved |= result.status() == RecognitionResult.Status.UNRESOLVED;
            }
            if (unresolved) return RecognitionResult.unresolved(version, "quantifier contains unresolved item: " + path);
            return RecognitionResult.of(op.equals("every") ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, version, path);
        }
        public Set<String> readPaths() { Set<String> result = new LinkedHashSet<>(); result.add(path); for (String child : condition.readPaths()) result.add(path + "." + child); return Set.copyOf(result); }
        public String canonical() { return op + "(" + path + "," + condition.canonical() + ")"; }
    }
    private record Lookup(boolean present, Object value) { }
    private static Lookup lookup(Object scope, String path) { Object current = scope; for (String part : path.split("\\.")) { if (current instanceof java.util.Map<?, ?> map && map.containsKey(part)) current = map.get(part); else return new Lookup(false, null); } return new Lookup(true, current); }
    private static java.math.BigDecimal decimal(Number value) { return new java.math.BigDecimal(value.toString()); }
    private static RecognitionResult combine(String op, RecognitionResult left, RecognitionResult right, String version) {
        if ("and".equals(op)) {
            if (left.status() == RecognitionResult.Status.ERROR) return merge(left, right, version, RecognitionResult.Status.ERROR);
            if (right.status() == RecognitionResult.Status.ERROR) return merge(left, right, version, RecognitionResult.Status.ERROR);
            if (left.status() == RecognitionResult.Status.FALSE || right.status() == RecognitionResult.Status.FALSE) return merge(left, right, version, RecognitionResult.Status.FALSE);
            if (left.status() == RecognitionResult.Status.UNRESOLVED || right.status() == RecognitionResult.Status.UNRESOLVED) return merge(left, right, version, RecognitionResult.Status.UNRESOLVED);
            return merge(left, right, version, RecognitionResult.Status.TRUE);
        }
        if (left.status() == RecognitionResult.Status.ERROR) return merge(left, right, version, RecognitionResult.Status.ERROR);
        if (right.status() == RecognitionResult.Status.ERROR) return merge(left, right, version, RecognitionResult.Status.ERROR);
        if (left.status() == RecognitionResult.Status.TRUE || right.status() == RecognitionResult.Status.TRUE) return merge(left, right, version, RecognitionResult.Status.TRUE);
        if (left.status() == RecognitionResult.Status.UNRESOLVED || right.status() == RecognitionResult.Status.UNRESOLVED) return merge(left, right, version, RecognitionResult.Status.UNRESOLVED);
        return merge(left, right, version, RecognitionResult.Status.FALSE);
    }
    private static RecognitionResult merge(RecognitionResult left, RecognitionResult right, String version, RecognitionResult.Status status) {
        java.util.LinkedHashSet<String> evidence = new java.util.LinkedHashSet<>(left.evidence()); evidence.addAll(right.evidence());
        java.util.LinkedHashSet<String> errors = new java.util.LinkedHashSet<>(left.errors()); errors.addAll(right.errors());
        java.util.ArrayList<TraceEvent> trace = new java.util.ArrayList<>(left.trace()); trace.addAll(right.trace());
        java.util.LinkedHashSet<InformationKey> dependencies = new java.util.LinkedHashSet<>(left.dependencies()); dependencies.addAll(right.dependencies());
        return new RecognitionResult(status, evidence.stream().toList(), dependencies.stream().toList(), errors.stream().toList(), version, trace, left.failure() != null ? left.failure() : right.failure());
    }
}
