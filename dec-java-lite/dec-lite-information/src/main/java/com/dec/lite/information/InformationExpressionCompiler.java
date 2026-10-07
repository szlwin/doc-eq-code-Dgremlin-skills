package com.dec.lite.information;

import java.util.ArrayList;
import java.util.List;

/** Compiler for InformationKey-only expressions. Model fields and literals are rejected. */
public final class InformationExpressionCompiler {
    public InformationExpression compile(String source) {
        if (source == null || source.isBlank()) throw new InformationCompilationException("Information expression is empty");
        Parser parser = new Parser(source);
        InformationExpression value = parser.parseOr();
        parser.expectEnd();
        return value;
    }
    private static final class Parser {
        private final List<String> tokens = new ArrayList<>(); private int index;
        Parser(String source) { StringBuilder current = new StringBuilder(); for (int i = 0; i < source.length(); i++) { char c = source.charAt(i); if (Character.isWhitespace(c)) flush(current); else if (c == '(' || c == ')') { flush(current); tokens.add(String.valueOf(c)); } else current.append(c); } flush(current); }
        private void flush(StringBuilder current) { if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); } }
        InformationExpression parseOr() { InformationExpression left = parseAnd(); while (accept("or")) left = InformationExpression.or(left, parseAnd()); return left; }
        InformationExpression parseAnd() { InformationExpression left = parseUnary(); while (accept("and")) left = InformationExpression.and(left, parseUnary()); return left; }
        InformationExpression parseUnary() { if (accept("not")) return InformationExpression.not(parseUnary()); return parsePrimary(); }
        InformationExpression parsePrimary() { if (accept("(")) { InformationExpression value = parseOr(); require(")"); return value; } String token = next(); if (token.equals("and") || token.equals("or") || token.equals("not") || token.equals(")") || !token.matches("[A-Za-z_][A-Za-z0-9_-]*\\.[A-Za-z_][A-Za-z0-9_-]*")) throw error("only qualified InformationKey references are allowed: " + token); return InformationExpression.reference(InformationKey.parse(token)); }
        String next() { if (index >= tokens.size()) throw error("unexpected end"); return tokens.get(index++); }
        boolean accept(String token) { if (index < tokens.size() && token.equals(tokens.get(index))) { index++; return true; } return false; }
        void require(String token) { if (!accept(token)) throw error("expected " + token); }
        void expectEnd() { if (index != tokens.size()) throw error("unexpected token " + tokens.get(index)); }
        InformationCompilationException error(String message) { return new InformationCompilationException(message); }
    }
}
