package com.dec.lite.information;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NumericComparisonTest {
    @Test void orderedComparisonsRespectDecimalBoundariesAndDoNotRoundLargeIntegers() {
        assertStatus("amount<=1000", new BigDecimal("1000.00"), RecognitionResult.Status.TRUE);
        assertStatus("amount<=1000", new BigDecimal("1000.0000000000000001"), RecognitionResult.Status.FALSE);
        assertStatus("amount>1000", new BigDecimal("1000.0000000000000001"), RecognitionResult.Status.TRUE);
        assertStatus("amount>=-1.25", new BigDecimal("-1.250"), RecognitionResult.Status.TRUE);
        assertStatus("amount<-1.25", new BigDecimal("-1.251"), RecognitionResult.Status.TRUE);
        assertStatus("amount=9007199254740993", 9007199254740992L, RecognitionResult.Status.FALSE);
        assertStatus("amount!=9007199254740993", 9007199254740992L, RecognitionResult.Status.TRUE);
        assertStatus("amount=1.00", 1L, RecognitionResult.Status.TRUE);
    }
    @Test void missingAndNonNumericValuesFailClosedAndQuantifiersUseNumericComparisons() {
        for (Object value : List.of("1000", true, Double.NaN, Double.POSITIVE_INFINITY))
            assertStatus("amount<1001", value, RecognitionResult.Status.ERROR);
        var compiler = new ModelExpressionCompiler();
        assertEquals(RecognitionResult.Status.UNRESOLVED, compiler.compile("amount<1001").evaluate(new ModelContext(Map.of())).status());
        assertEquals(RecognitionResult.Status.ERROR, compiler.compile("amount<1001").evaluate(new ModelContext(java.util.Collections.singletonMap("amount", null))).status());
        assertEquals(RecognitionResult.Status.FALSE, compiler.compile("every(lines, amount<=1000)")
                .evaluate(new ModelContext(Map.of("lines", List.of(Map.of("amount", 500), Map.of("amount", 1001))))).status());
    }
    private void assertStatus(String source, Object value, RecognitionResult.Status expected) {
        assertEquals(expected, new ModelExpressionCompiler().compile(source).evaluate(new ModelContext(Map.of("amount", value))).status(), source);
    }
}
