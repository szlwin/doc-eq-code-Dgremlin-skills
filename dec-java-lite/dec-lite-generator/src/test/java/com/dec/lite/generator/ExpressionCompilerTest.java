package com.dec.lite.generator;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ExpressionCompilerTest {
    private final Map<String, String> fields = Map.of("format", "this.format", "compress", "this.compress");

    @Test void generatesNullSafeFieldAndLiteralComparisons() {
        String code = ExpressionCompiler.compile("format = csv and compress = true", fields, Set.of());
        assertEquals("(Objects.equals(this.format, \"csv\") && Objects.equals(this.compress, true))", code);
        assertEquals("Boolean.TRUE.equals(this.compress)", ExpressionCompiler.compile("compress", fields, Set.of()));
    }

    @Test void rejectsUnknownInputsAndHostLanguageSyntax() {
        for (String source : java.util.List.of("unknown = csv", "format =", "format = csv; System.exit(0)", "(compress", ""))
            assertThrows(GenerationException.class, () -> ExpressionCompiler.compile(source, fields, Set.of()), source);
    }
}
