package com.dec.lite.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;

/** Compiles the small boolean expression subset used by API request validation. */
final class ExpressionCompiler {
    private final List<Token> tokens;
    private final Map<String, String> fieldAccess;
    private final Set<String> enumFields;
    private int position;

    private ExpressionCompiler(String expression, Map<String, String> fieldAccess, Set<String> enumFields) {
        this.tokens = tokenize(expression);
        this.fieldAccess = fieldAccess;
        this.enumFields = enumFields;
    }

    static String compile(String expression, Map<String, String> fieldAccess, Set<String> enumFields) {
        if (expression == null || expression.isBlank()) throw new GenerationException("DESIGN_GAP empty request expression");
        ExpressionCompiler parser = new ExpressionCompiler(expression, fieldAccess, enumFields);
        String result = parser.parseOr();
        if (!parser.peek("EOF")) throw parser.error("unexpected token " + parser.current().text());
        return result;
    }

    private String parseOr() {
        String result = parseAnd();
        while (peek("OR")) {
            consume();
            result = "(" + result + " || " + parseAnd() + ")";
        }
        return result;
    }

    private String parseAnd() {
        String result = parseComparison();
        while (peek("AND")) {
            consume();
            result = "(" + result + " && " + parseComparison() + ")";
        }
        return result;
    }

    private String parseComparison() {
        Operand left = parseOperand(false);
        if (peek("EQ") || peek("NE")) {
            String operator = consume().kind();
            Operand right = parseOperand(true);
            String rightCode = right.code();
            if (left.fieldName() != null && enumFields.contains(left.fieldName()) && right.literal() != null
                    && right.literal().matches("-?\\d+(?:\\.\\d+)?")) {
                rightCode = javaString(right.literal());
            }
            return ("NE".equals(operator) ? "!" : "") + "Objects.equals(" + left.code() + ", " + rightCode + ")";
        }
        if (left.code().startsWith("this.")) return "Boolean.TRUE.equals(" + left.code() + ")";
        return left.code();
    }

    private Operand parseOperand(boolean rightSide) {
        if (peek("LPAREN")) {
            consume();
            String nested = parseOr();
            expect("RPAREN");
            return new Operand("(" + nested + ")", null, null);
        }
        Token token = current();
        if (peek("IDENT")) {
            consume();
            if (fieldAccess.containsKey(token.text())) return new Operand(fieldAccess.get(token.text()), token.text(), null);
            if (rightSide) return new Operand(javaString(token.text()), null, token.text());
            throw error("unknown expression field " + token.text());
        }
        if (peek("LITERAL")) {
            consume();
            return new Operand(javaLiteral(token.text()), null, token.text());
        }
        throw error("expected field or literal");
    }

    private Token expect(String kind) {
        if (!peek(kind)) throw error("expected " + kind + " but found " + current().text());
        return consume();
    }

    private Token consume() {
        return tokens.get(position++);
    }

    private boolean peek(String kind) {
        return current().kind().equals(kind);
    }

    private Token current() {
        return tokens.get(position);
    }

    private GenerationException error(String message) {
        return new GenerationException("DESIGN_GAP invalid request expression: " + message);
    }

    private static List<Token> tokenize(String expression) {
        List<Token> result = new ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) index++;
            if (index >= expression.length()) break;
            char c = expression.charAt(index);
            if (c == '(') { result.add(new Token("LPAREN", "(")); index++; continue; }
            if (c == ')') { result.add(new Token("RPAREN", ")")); index++; continue; }
            if (c == '=' || c == '!') {
                int start = index++;
                if (index < expression.length() && expression.charAt(index) == '=') index++;
                result.add(new Token(c == '!' ? "NE" : "EQ", expression.substring(start, index)));
                continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                int start = index++;
                while (index < expression.length() && expression.charAt(index) != quote) {
                    if (expression.charAt(index) == '\\' && index + 1 < expression.length()) index++;
                    index++;
                }
                if (index >= expression.length()) throw new GenerationException("DESIGN_GAP unterminated expression string");
                index++;
                result.add(new Token("LITERAL", expression.substring(start, index)));
                continue;
            }
            if (Character.isDigit(c) || (c == '-' && index + 1 < expression.length() && Character.isDigit(expression.charAt(index + 1)))) {
                int start = index++;
                while (index < expression.length() && (Character.isDigit(expression.charAt(index)) || expression.charAt(index) == '.')) index++;
                result.add(new Token("LITERAL", expression.substring(start, index)));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = index++;
                while (index < expression.length() && (Character.isLetterOrDigit(expression.charAt(index)) || expression.charAt(index) == '_' || expression.charAt(index) == '.')) index++;
                String text = expression.substring(start, index);
                String upper = text.toUpperCase();
                result.add(new Token("AND".equals(upper) ? "AND" : "OR".equals(upper) ? "OR"
                        : ("NULL".equals(upper) || "TRUE".equals(upper) || "FALSE".equals(upper)) ? "LITERAL" : "IDENT", text));
                continue;
            }
            throw new GenerationException("DESIGN_GAP unsupported character in request expression: " + c);
        }
        result.add(new Token("EOF", "<eof>"));
        return result;
    }

    private static String javaLiteral(String value) {
        if (value.equalsIgnoreCase("null")) return "null";
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return value.toLowerCase();
        if (value.startsWith("\"") || value.startsWith("'")) {
            String inner = value.substring(1, value.length() - 1).replace("\\'", "'").replace("\\\"", "\"");
            return javaString(inner);
        }
        return value;
    }

    private static String javaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record Token(String kind, String text) { }
    private record Operand(String code, String fieldName, String literal) { }
}
