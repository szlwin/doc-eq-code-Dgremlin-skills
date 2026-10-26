package com.dec.lite.query;

import com.dec.lite.information.*;
import com.dec.lite.model.SemanticDigest;
import com.dec.lite.parser.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Python validates and exports each variant; Java consumes both forms and checks observable behavior. */
class SemanticVariantsParityTest {
    @TempDir Path temp;

    @SuppressWarnings("unchecked")
    @Test void pythonExportJavaRuntimeAndSqlProjectionAgreeOnVariantsAndCounterexamples() throws Exception {
        var log = temp.resolve("python.log");
        var process = new ProcessBuilder(System.getenv().getOrDefault("PYTHON", "python3"),
                "../tools/prepare_semantic_variants.py", temp.resolve("variants").toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS), "Python fixture preparation timed out");
        assertEquals(0, process.exitValue(), Files.readString(log));
        // JSON is a YAML subset; reuse the existing test dependency, without another parser library.
        var cases = (List<Map<String, Object>>) new org.yaml.snakeyaml.Yaml().load(Files.readString(Path.of("../tools/semantic-cases.json")));
        for (var entry : cases) {
            String name = (String) entry.get("name");
            Path root = temp.resolve("variants").resolve(name);
            var yaml = new DecYamlParser().parse(root.resolve("yaml"));
            var xml = new DecXmlParser().parse(root.resolve("xml"));
            assertEquals(SemanticDigest.project(yaml), SemanticDigest.project(xml), name);
            SqlPredicate previous = null;
            for (var project : List.of(yaml, xml)) {
                assertTrue(project.getDesignGaps().isEmpty(), name);
                var information = new InformationParser().parse(project);
                var engine = new InformationEngine(information);
                var key = InformationKey.parse("payment.success");
                assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key,
                        new ModelContext((Map<String, Object>) entry.get("yes"))).status(), name);
                assertEquals(RecognitionResult.Status.FALSE, engine.evaluate(key,
                        new ModelContext((Map<String, Object>) entry.get("no"))).status(), name);
                var schema = QuerySchema.compile(project);
                var result = new InformationPredicateCompiler(information,
                        new ViewJoinPlanner(schema, schema.view("OrderInfo")), "OrderInfo", new AtomicInteger()).compile(key);
                assertEquals(entry.get("coverage"), result.coverage().name(), name);
                if (previous != null) assertEquals(previous, result.predicate(), name);
                previous = result.predicate();
            }
        }
    }
}
