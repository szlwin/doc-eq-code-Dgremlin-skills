package com.dec.lite.query;

import com.dec.lite.information.*;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InformationPredicateCompilerTest {
    private InformationPredicateCompiler.Result compile(String source) throws Exception {
        var key = InformationKey.parse("user.literal");
        var definition = new InformationDefinition("INFO-LITERAL", key, "OrderInfo", null, source, null, null, Path.of("test"));
        var expression = new ModelExpressionCompiler().compile(source);
        var information = new InformationCompilation(Map.of(key, definition), Map.of(key, expression),
                Map.of(), Map.of(), Map.of(), List.of(key), "test");
        var project = new DecYamlParser().parse(Path.of(getClass().getClassLoader().getResource("mix").toURI()));
        var schema = QuerySchema.compile(project);
        return new InformationPredicateCompiler(information, new ViewJoinPlanner(schema, schema.view("OrderInfo")),
                "OrderInfo", new AtomicInteger()).compile(key);
    }

    @Test void literalsUseTheSameParsedValueAsRuntimeWithoutWhitespaceOrKeywordRewriting() throws Exception {
        for (String value : List.of("a  b", "a\tb", "a\nb", "a and b", "a or b", "not ready", "a\\'b", "a\\\\b")) {
            String source = "user.name = '" + value + "'";
            assertEquals(RecognitionResult.Status.TRUE, new ModelExpressionCompiler().compile(source)
                    .evaluate(new ModelContext(Map.of("user", Map.of("name", value)))).status());
            var pushed = compile(source);
            assertEquals(InformationPredicateCompiler.Coverage.FULL, pushed.coverage(), source);
            var comparison = assertInstanceOf(SqlPredicate.Compare.class, pushed.predicate());
            assertEquals(value, comparison.parameters().getFirst().value(), source);
        }
    }

    @Test void onlyNecessaryTopLevelConjunctsArePushed() throws Exception {
        var partial = compile("status = 2 and (user.name = 'a and b' or user.name = 'c')");
        assertEquals(InformationPredicateCompiler.Coverage.PARTIAL, partial.coverage());
        assertEquals("status", assertInstanceOf(SqlPredicate.Compare.class, partial.predicate()).field().path());
        for (String source : List.of("not (status = 2)", "status = 2 or user.name = 'x'", "user.name != 'x'",
                "every(orderDetailList, status = 2 and id = 3)")) {
            assertEquals(InformationPredicateCompiler.Coverage.RUNTIME_ONLY, compile(source).coverage(), source);
        }
        assertEquals(InformationPredicateCompiler.Coverage.FULL,
                compile("status = 2 and user.name = 'a  b'").coverage());
        assertTrue(assertInstanceOf(SqlPredicate.Compare.class, compile("user.name = null").predicate()).parameters().isEmpty());
    }
}
