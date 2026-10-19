package com.dec.lite.action;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RuleChecksTest {
    @Test void compoundChecksPreserveNullAndExactNumericBoundaries() {
        var program = RuleChecks.compile("NOTNULL;GREATEREQUAL:2;LESS:3;NOTEQUAL:2.5");
        assertFalse(program.test(null));
        assertTrue(program.test(2L));
        assertTrue(program.test(new BigDecimal("2.0000000000000000001")));
        assertFalse(program.test(new BigDecimal("2.5")));
        assertFalse(program.test(3));
        assertTrue(RuleChecks.test(null, "NULL"));
        assertTrue(RuleChecks.test("a;b", "EQUAL:'a;b'"));
        var rule = new CompiledRule("R", "check", "check", "value", "NOTNULL", null, "db");
        assertSame(rule.checks(), rule.withDataSource("other").checks());
    }
    @Test void invalidPatternsFailBeforeEvaluation() {
        for (String value : List.of(";", "NOTNULL:1", "GREATER", "EQUAL:", "UNKNOWN", "LESS:1.2.3"))
            assertThrows(ActionExecutionException.class, () -> RuleChecks.compile(value), value);
    }
}
