package com.dec.lite.action;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuleExpressionNullTest {
    @Test void absentOptionalParentIsNullButUnloadedOrMisspelledFieldsAreUnresolved() {
        var absent = new RuleExpression.Scope(Collections.singletonMap("payInfo", null), Map.of());
        assertEquals(false, RuleExpression.compile("payInfo.resultCode = 1").evaluate(absent));
        assertEquals(true, RuleExpression.compile("payInfo.resultCode = null").evaluate(absent));
        assertEquals(false, RuleExpression.compile("exists(payInfo.resultCode)").evaluate(absent));
        assertThrows(RuleExpression.MissingValue.class, () -> RuleExpression.compile("unknown.resultCode = 1").evaluate(absent));
        var present = new RuleExpression.Scope(Map.of("payInfo", Map.of("resultCode", 1)), Map.of());
        assertEquals(true, RuleExpression.compile("payInfo.resultCode = 1").evaluate(present));
        assertThrows(RuleExpression.MissingValue.class, () -> RuleExpression.compile("payInfo.typo = 1").evaluate(present));
    }
}
