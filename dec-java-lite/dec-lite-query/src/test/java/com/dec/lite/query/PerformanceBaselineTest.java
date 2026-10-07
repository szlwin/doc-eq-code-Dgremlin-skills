package com.dec.lite.query;

import com.dec.lite.action.ActionRuntime;
import com.dec.lite.action.CustomActionRegistry;
import com.dec.lite.directory.*;
import com.dec.lite.information.*;
import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import com.dec.lite.session.ExecutionSession;
import com.dec.lite.session.TransactionPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Repeatable local P8 smoke baseline; CI archives target/performance/p8-baseline.csv for trends. */
class PerformanceBaselineTest {
    @Test void measuresCoreOperationsAgainstGenerousReleaseGuards() throws Exception {
        Path fixture = Path.of(getClass().getClassLoader().getResource("mix").toURI());
        long parseStart = System.nanoTime();
        DecProject project = null; InformationCompilation information = null; DirectoryGraph graph = null;
        for (int i = 0; i < 5; i++) {
            project = new DecYamlParser().parse(fixture);
            information = new InformationParser().parse(project);
            graph = new DirectoryGraphCompiler().compile(project, information);
        }
        double parseCompileMs = ms(System.nanoTime() - parseStart) / 5;
        assertEquals(16, information.definitions().size());

        QueryCompiler queries = new QueryCompiler(project, graph, information);
        long queryStart = System.nanoTime();
        for (int i = 0; i < 100; i++) queries.compile(DirectoryQuery.find("PayResult").with("orderDetailList"));
        double queryMs = ms(System.nanoTime() - queryStart) / 100;

        InformationEngine engine = new InformationEngine(information);
        ModelContext model = model(); InformationKey ordered = new InformationKey("order", "ordered");
        long firstStart = System.nanoTime();
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(ordered, model).status());
        double firstMs = ms(System.nanoTime() - firstStart);
        long incrementalStart = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            MutationSet mutations = new MutationSet(); model.write("status", i % 2 == 0 ? 2L : 1L, mutations);
            engine.invalidate(mutations); engine.evaluate(ordered, model);
        }
        double incrementalMs = ms(System.nanoTime() - incrementalStart) / 100;

        var definition = new DirectoryDefinition("DIR-BENCH", "root", "normal", "OrderInfo", ordered,
                true, List.of(), List.of(), null, fixture);
        DirectoryEngine directory = new DirectoryEngine(new DirectoryGraph(Map.of("root", definition), List.of(), List.of(), List.of(), "root"));
        long directoryStart = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            try (ExecutionSession session = new ExecutionSession(new InformationEngine(information),
                    new ActionRuntime(new RuleViewRegistry(), new CustomActionRegistry()), model(), Map.of(),
                    Instant.MAX, null, TransactionPolicy.NONE, null)) {
                assertTrue(directory.executeTo("root", new DirectoryExecutionContext(session)).success());
            }
        }
        double directoryMs = ms(System.nanoTime() - directoryStart) / 50;

        long concurrencyStart = System.nanoTime();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int t = 0; t < 4; t++) jobs.add(pool.submit(() -> {
                for (int i = 0; i < 50; i++) assertNotNull(queries.compile(DirectoryQuery.find("PayResult")));
            }));
            for (var job : jobs) job.get();
        }
        double concurrentMs = ms(System.nanoTime() - concurrencyStart) / 200;

        long heapBefore = usedHeap();
        RuntimeCatalog catalog = new RuntimeCatalog(fixture, p -> new com.dec.lite.action.RuleViewCompiler().register(p, null),
                p -> new CustomActionRegistry().register(new com.dec.lite.action.CustomAction() {
                    public String name() { return "smsNotify"; }
                    public void execute(com.dec.lite.action.ActionExecutionContext context) { }
                }));
        assertNotNull(catalog.current());
        long contextBytes = Math.max(0, usedHeap() - heapBefore);
        Path report = Path.of("target", "performance", "p8-baseline.csv");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "metric,value,unit,guard\n" +
                "parse_compile," + parseCompileMs + ",ms_per_full_mix,5000\n" +
                "information_first," + firstMs + ",ms,1000\n" +
                "information_incremental," + incrementalMs + ",ms_per_recompute,1000\n" +
                "directory," + directoryMs + ",ms_per_path,1000\n" +
                "query_compile," + queryMs + ",ms_per_query,1000\n" +
                "concurrent_read," + concurrentMs + ",ms_per_query,1000\n" +
                "context_heap," + contextBytes + ",bytes,134217728\n");
        assertTrue(parseCompileMs < 5000);
        assertTrue(firstMs < 1000);
        assertTrue(incrementalMs < 1000);
        assertTrue(directoryMs < 1000);
        assertTrue(queryMs < 1000);
        assertTrue(concurrentMs < 1000);
        assertTrue(contextBytes < 134_217_728);
    }
    private static ModelContext model() {
        return new ModelContext(Map.of("status", 1L,
                "orderDetailList", List.of(Map.of("status", 1L), Map.of("status", 1L))));
    }
    private static long usedHeap() { Runtime runtime = Runtime.getRuntime(); return runtime.totalMemory() - runtime.freeMemory(); }
    private static double ms(long nanos) { return nanos / 1_000_000.0; }
}
