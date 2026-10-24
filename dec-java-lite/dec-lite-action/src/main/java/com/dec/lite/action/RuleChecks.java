package com.dec.lite.action;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/** PropertyCheck's compound checks; unknown validators are rejected instead of becoming equality. */
final class RuleChecks {
    private record Check(String name, Object expected) { }
    static void validate(String pattern) { compile(pattern); }
    static boolean test(Object actual, String pattern) { return compile(pattern).test(actual); }
    static Program compile(String pattern) { return new Program(parse(pattern)); }
    record Program(List<Check> checks) {
        Program { checks = List.copyOf(checks); }
        boolean test(Object actual) {
        for (Check check : checks) {
            boolean success = switch (check.name()) {
                case "NOTNULL" -> actual != null;
                case "NULL" -> actual == null;
                case "EQUAL" -> RuleExpression.equal(actual, check.expected());
                case "NOTEQUAL" -> !RuleExpression.equal(actual, check.expected());
                case "GREATER" -> actual != null && compareNumbers(check.name(), actual, check.expected()) > 0;
                case "GREATEREQUAL" -> actual != null && compareNumbers(check.name(), actual, check.expected()) >= 0;
                case "LESS" -> actual != null && compareNumbers(check.name(), actual, check.expected()) < 0;
                case "LESSEQUAL" -> actual != null && compareNumbers(check.name(), actual, check.expected()) <= 0;
                default -> throw new ActionExecutionException("Unsupported property check " + check.name());
            };
            if (!success) return false;
        }
        return true;
        }
    }

    private static int compareNumbers(String checkName, Object actual, Object expected) {
        try {
            return RuleExpression.number(actual).compareTo(RuleExpression.number(expected));
        } catch (ActionExecutionException failure) {
            throw new ActionExecutionException("Property check " + checkName + " requires numeric operands: "
                    + failure.getMessage(), failure);
        }
    }

    private static List<Check> parse(String pattern) {
        if (pattern == null || pattern.isBlank()) throw new ActionExecutionException("check requires pattern");
        var result = new java.util.ArrayList<Check>();
        for (String part : RuleText.split(pattern, ';')) {
            if (part.isBlank()) continue;
            int colon = part.indexOf(':');
            String name = (colon < 0 ? part : part.substring(0, colon)).trim().toUpperCase(Locale.ROOT);
            if (!Set.of("NOTNULL", "NULL", "EQUAL", "NOTEQUAL", "GREATER", "GREATEREQUAL", "LESS", "LESSEQUAL").contains(name))
                throw new ActionExecutionException("Unsupported property check: " + name);
            boolean unary = name.equals("NOTNULL") || name.equals("NULL");
            if (unary != (colon < 0)) throw new ActionExecutionException("Invalid check operand: " + part);
            Object operand = unary ? null : RuleExpression.compile(part.substring(colon + 1)).evaluate(new RuleExpression.Scope(Map.of(), Map.of()));
            result.add(new Check(name, operand));
        }
        if (result.isEmpty()) throw new ActionExecutionException("empty property check");
        return List.copyOf(result);
    }
}
