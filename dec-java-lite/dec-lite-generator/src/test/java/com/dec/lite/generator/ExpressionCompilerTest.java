package com.dec.lite.generator;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ExpressionCompilerTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
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

    @Test void compiledJavaAndRuntimeAgreeOnSharedLiteralSubset() throws Exception {
        String[] expressions = {"value = 1", "value = 12.50", "value = -1", "value = 'a  b'", "value = null", "value = 'approved'"};
        Object[][] values = {{1, 1L, 2L}, {new java.math.BigDecimal("12.500"), 12.5, new java.math.BigDecimal("12.51")},
                {-1L, 1L}, {"a  b", "a b"}, {null, "null"}, {"approved", "rejected"}};
        for (int i = 0; i < expressions.length; i++) {
            String className = "Literal" + i;
            String code = ExpressionCompiler.compile(expressions[i], Map.of("value", "value"), Set.of());
            var file = temp.resolve(className + ".java");
            java.nio.file.Files.writeString(file, "import java.util.Objects; public class " + className
                    + " { public static boolean check(Object value) { return " + code + "; } }");
            assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-classpath", System.getProperty("java.class.path"), "-d", temp.toString(), file.toString()));
            try (var loader = new java.net.URLClassLoader(new java.net.URL[]{temp.toUri().toURL()}, getClass().getClassLoader())) {
                var method = loader.loadClass(className).getMethod("check", Object.class);
                var runtime = new com.dec.lite.information.ModelExpressionCompiler().compile(expressions[i]);
                for (Object value : values[i]) {
                    var model = new java.util.HashMap<String, Object>(); model.put("value", value);
                    boolean expected = runtime.evaluate(new com.dec.lite.information.ModelContext(model)).status()
                            == com.dec.lite.information.RecognitionResult.Status.TRUE;
                    assertEquals(expected, method.invoke(null, value), expressions[i] + " for " + value);
                }
            }
        }
    }
}
