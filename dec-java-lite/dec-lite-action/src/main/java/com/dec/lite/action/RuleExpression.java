package com.dec.lite.action;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

/** Rule expressions only: no reflection, class loading or executable host-language code. */
final class RuleExpression {
    @FunctionalInterface interface Expression { Object evaluate(Scope scope); }
    record Scope(Map<String, Object> model, Map<String, Object> variables) {
        Object read(String path) {
            if (path.startsWith("#")) {
                if (!variables.containsKey(path)) throw new MissingValue(path);
                return variables.get(path);
            }
            Object value = model;
            for (String part : path.split("\\.")) {
                int bracket = part.indexOf('[');
                String key = bracket < 0 ? part : part.substring(0, bracket);
                if (!(value instanceof Map<?, ?> map) || !map.containsKey(key)) throw new MissingValue(path);
                value = map.get(key);
                if (bracket >= 0) {
                    int index = Integer.parseInt(part.substring(bracket + 1, part.length() - 1));
                    if (!(value instanceof List<?> list) || index >= list.size()) throw new MissingValue(path);
                    value = list.get(index);
                }
            }
            return value;
        }
    }
    static final class MissingValue extends ActionExecutionException {
        MissingValue(String path) { super("missing Rule value: " + path); }
    }
    static Expression compile(String source) {
        Parser parser = new Parser(source);
        Expression expression = parser.expression();
        if (!parser.peek("<end>")) throw parser.error("unexpected token " + parser.current());
        return expression;
    }
    static boolean bool(Object value) {
        if (!(value instanceof Boolean b)) throw new ActionExecutionException("Rule expression must produce a boolean: " + value);
        return b;
    }
    static BigDecimal number(Object value) {
        if (!(value instanceof Number n)) throw new ActionExecutionException("numeric Rule operand required: " + value);
        return new BigDecimal(n.toString());
    }
    static boolean equal(Object a, Object b) {
        return a instanceof Number && b instanceof Number ? number(a).compareTo(number(b)) == 0 : Objects.equals(a, b);
    }
    private record Token(String text, Object literal, boolean isLiteral) { }
    private static final class Parser {
        private final List<Token> tokens = new ArrayList<>();
        private int index;
        Parser(String source) {
            if (source == null || source.isBlank()) throw new ActionExecutionException("empty Rule expression");
            for (int i = 0; i < source.length();) {
                char c = source.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == '\'' || c == '"') {
                    char quote = c; var text = new StringBuilder(); boolean closed = false; i++;
                    while (i < source.length()) {
                        char next = source.charAt(i++);
                        if (next == quote) { closed = true; break; }
                        if (next == '\\') {
                            if (i == source.length()) throw error("unfinished escape");
                            next = source.charAt(i++);
                            next = switch (next) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> next; };
                        }
                        text.append(next);
                    }
                    if (!closed) throw error("unterminated string");
                    tokens.add(new Token("<string>", text.toString(), true)); continue;
                }
                if (Character.isDigit(c)) {
                    int start = i++;
                    while (i < source.length() && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
                    String text = source.substring(start, i);
                    Object number = text.contains(".") ? new BigDecimal(text) : Long.valueOf(text);
                    tokens.add(new Token(text, number, true)); continue;
                }
                if (Character.isLetter(c) || c == '_' || c == '#') {
                    int start = i++;
                    while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i)) || "_.[]".indexOf(source.charAt(i)) >= 0)) i++;
                    String text = source.substring(start, i);
                    if (text.equals("null")) tokens.add(new Token(text, null, true));
                    else if (text.equals("true") || text.equals("false")) tokens.add(new Token(text, Boolean.valueOf(text), true));
                    else tokens.add(new Token(text, null, false));
                    continue;
                }
                String pair = i + 1 < source.length() ? source.substring(i, i + 2) : "";
                if (Set.of(">=", "<=", "!=", "==", "<>", "&&", "||").contains(pair)) {
                    tokens.add(new Token(pair, null, false)); i += 2; continue;
                }
                if ("()+-*/%=<>!,".indexOf(c) < 0) throw error("unsupported character " + c);
                tokens.add(new Token(String.valueOf(c), null, false)); i++;
            }
            tokens.add(new Token("<end>", null, false));
        }
        String current() { return index < tokens.size() ? tokens.get(index).text() : "<end>"; }
        boolean peek(String value) { return current().equals(value); }
        boolean accept(String value) { if (!peek(value)) return false; index++; return true; }
        void require(String value) { if (!accept(value)) throw error("expected " + value); }
        ActionExecutionException error(String text) { return new ActionExecutionException("Invalid Rule expression: " + text); }
        Expression expression() {
            if (accept("if")) {
                Expression condition = or(); require("then"); Expression yes = expression(); require("else"); Expression no = expression();
                return scope -> bool(condition.evaluate(scope)) ? yes.evaluate(scope) : no.evaluate(scope);
            }
            return or();
        }
        Expression or() {
            Expression left = and();
            while (peek("or") || peek("||")) { index++; Expression a = left, b = and(); left = s -> bool(a.evaluate(s)) || bool(b.evaluate(s)); }
            return left;
        }
        Expression and() {
            Expression left = compare();
            while (peek("and") || peek("&&")) { index++; Expression a = left, b = compare(); left = s -> bool(a.evaluate(s)) && bool(b.evaluate(s)); }
            return left;
        }
        Expression compare() {
            if (accept("not") || accept("!")) { Expression next = compare(); return s -> !bool(next.evaluate(s)); }
            Expression left = add();
            if (Set.of("=", "==", "!=", "<>", "<", "<=", ">", ">=").contains(current())) {
                String op = current(); index++; Expression right = add();
                return scope -> {
                    Object a = left.evaluate(scope), b = right.evaluate(scope);
                    if (op.equals("=") || op.equals("==")) return equal(a, b);
                    if (op.equals("!=") || op.equals("<>")) return !equal(a, b);
                    int cmp = number(a).compareTo(number(b));
                    return switch (op) { case "<" -> cmp < 0; case "<=" -> cmp <= 0; case ">" -> cmp > 0; default -> cmp >= 0; };
                };
            }
            return left;
        }
        Expression add() {
            Expression left = multiply();
            while (peek("+") || peek("-")) {
                String op = current(); index++; Expression a = left, b = multiply();
                left = s -> op.equals("+") ? number(a.evaluate(s)).add(number(b.evaluate(s))) : number(a.evaluate(s)).subtract(number(b.evaluate(s)));
            }
            return left;
        }
        Expression multiply() {
            Expression left = unary();
            while (peek("*") || peek("/") || peek("%")) {
                String op = current(); index++; Expression a = left, b = unary();
                left = s -> switch (op) {
                    case "*" -> number(a.evaluate(s)).multiply(number(b.evaluate(s)));
                    case "/" -> number(a.evaluate(s)).divide(number(b.evaluate(s)), MathContext.DECIMAL128);
                    default -> number(a.evaluate(s)).remainder(number(b.evaluate(s)));
                };
            }
            return left;
        }
        Expression unary() {
            if (accept("-")) { Expression next = unary(); return s -> number(next.evaluate(s)).negate(); }
            if (accept("+")) { Expression next = unary(); return s -> number(next.evaluate(s)); }
            return primary();
        }
        Expression primary() {
            if (peek("<end>")) throw error("unexpected end");
            if (accept("(")) { Expression nested = expression(); require(")"); return nested; }
            Token token = tokens.get(index++);
            if (token.isLiteral()) return s -> token.literal();
            String name = token.text();
            if (name.equals("<end>")) throw error("unexpected end");
            if (accept("(")) {
                if (!Set.of("exists", "every", "any").contains(name)) throw error("unsupported function " + name);
                String path = current(); index++; validatePath(path);
                if (name.equals("exists")) {
                    require(")"); return s -> { try { return s.read(path) != null; } catch (MissingValue missing) { return false; } };
                }
                require(","); Expression predicate = expression(); require(")");
                return s -> {
                    Object value = s.read(path);
                    if (!(value instanceof List<?> list)) throw error("quantifier requires list: " + path);
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> map)) throw error("quantifier requires object items");
                        var row = new LinkedHashMap<String, Object>(); map.forEach((k,v) -> row.put(String.valueOf(k),v));
                        boolean result = bool(predicate.evaluate(new Scope(row, s.variables())));
                        if (name.equals("every") && !result) return false;
                        if (name.equals("any") && result) return true;
                    }
                    return name.equals("every");
                };
            }
            validatePath(name);
            return s -> s.read(name);
        }
        void validatePath(String path) {
            if (!path.matches("#?[A-Za-z_][A-Za-z0-9_]*(?:\\[\\d+])?(?:\\.[A-Za-z_][A-Za-z0-9_]*(?:\\[\\d+])?)*"))
                throw error("invalid property path " + path);
        }
    }
}
