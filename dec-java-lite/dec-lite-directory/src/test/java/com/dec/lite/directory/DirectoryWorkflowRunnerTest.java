package com.dec.lite.directory;

import com.dec.lite.action.*;
import com.dec.lite.information.ModelContext;
import com.dec.lite.parser.DecXmlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryWorkflowRunnerTest {
    @TempDir Path input;
    private static final DataOperationAdapter NO_WRITES = (rule, invocation) -> {
        throw new AssertionError("Recognition must not execute external mutations: " + rule.id());
    };

    @Test void runnerRejectsUnknownListenerTargetAtStartup() throws Exception {
        var project = new DecXmlParser().parse(Path.of(getClass().getClassLoader().getResource("mix-xml").toURI()));
        var listeners = ActionListenerRegistry.from(List.of(new ActionListener() {
            public String businessId() { return "BUS-TYPO"; }
        }));
        var failure = assertThrows(IllegalArgumentException.class,
                () -> new DirectoryWorkflowRunner(project, new CustomActionRegistry(), listeners));
        assertTrue(failure.getMessage().contains("BUS-TYPO"));
    }

    @Test void reusedRunnerDoesNotReadCanonicalDocumentsDuringRequests() throws Exception {
        var parsed = new DecXmlParser().parse(Path.of(getClass().getClassLoader().getResource("mix-xml").toURI()));
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var project = new com.dec.lite.model.DecProject() {
            @Override public List<com.dec.lite.model.DecDocument> getDocuments() {
                reads.incrementAndGet();
                return super.getDocuments();
            }
        };
        parsed.getDocuments().forEach(project::addDocument);
        var custom = CustomActionRegistry.from(List.of(new CustomAction() {
            public String name() { return "smsNotify"; }
            public void execute(ActionExecutionContext context) { }
        }));
        var runner = new DirectoryWorkflowRunner(project.freeze(), custom);
        reads.set(0);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var calls = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) calls.add(executor.submit(() -> {
                assertEquals("ordered", runner.recognizeDirectory(model(1, 1, null), NO_WRITES));
                assertEquals("success", runner.recognizeDirectory(model(3, 3, 1), NO_WRITES));
                // Current target is already satisfied: exercises execute() without external writes.
                var result = runner.run(new DirectoryWorkflowRunner.RunRequest("ordered", "ordered", false,
                        model(1, 1, null), Map.of(), NO_WRITES, custom,
                        WorkflowTransaction.externallyManaged(() -> new WorkflowTransaction.ExternalTransaction() {
                            public boolean isActive() { return true; }
                            public boolean isRollbackOnly() { return false; }
                            public void markRollbackOnly() { }
                        }), java.time.Instant.now().plusSeconds(30)));
                assertTrue(result.execution().success());
            }));
            for (var call : calls) call.get();
        }
        assertEquals(0, reads.get(), "Request execution must bind the existing compilation, not re-read the design");
    }

    @Test void recognizesStableStatesAndExcludesTransientResult() throws Exception {
        var runner = runner(Map.of());
        assertEquals("ordered", runner.recognizeDirectory(model(1, 1, null), NO_WRITES));
        assertEquals("paying", runner.recognizeDirectory(model(2, 2, null), NO_WRITES));
        assertEquals("success", runner.recognizeDirectory(model(3, 3, 1), NO_WRITES));
        assertEquals("error", runner.recognizeDirectory(model(4, 4, 2), NO_WRITES));
        runner.assertDirectoryMatches(model(3, 3, 1), "success", NO_WRITES);
        assertThrows(DirectoryExecutionException.class,
                () -> runner.assertDirectoryMatches(model(3, 3, 1), "paying", NO_WRITES));
    }

    @Test void rejectsInconsistentAndUnknownStates() throws Exception {
        var runner = runner(Map.of());
        assertThrows(DirectoryExecutionException.class, () -> runner.recognizeDirectory(model(1, 2, null), NO_WRITES));
        assertThrows(DirectoryExecutionException.class, () -> runner.recognizeDirectory(model(3, 3, 2), NO_WRITES));
        assertThrows(DirectoryExecutionException.class, () -> runner.recognizeDirectory(model(99, 99, null), NO_WRITES));
    }

    @Test void rejectsAmbiguousInformationInsteadOfChoosingFirstMatch() throws Exception {
        var runner = runner(Map.of("business-order-payment.xml", Map.of(
                "name=\"paying\" information-ref=\"order.paying\"", "name=\"paying\" information-ref=\"order.ordered\"")));
        var error = assertThrows(DirectoryExecutionException.class,
                () -> runner.recognizeDirectory(model(1, 1, null), NO_WRITES));
        assertTrue(error.getMessage().contains("ordered=TRUE"));
        assertTrue(error.getMessage().contains("paying=TRUE"));
    }

    @Test void unresolvedCompetingStateFailsClosed() throws Exception {
        var runner = runner(Map.of("systems-order.xml", Map.of(
                "payment.success&#10;and&#10;order.paySuccessStatus&#10;", "payment.success")));
        var error = assertThrows(DirectoryExecutionException.class,
                () -> runner.recognizeDirectory(model(1, 1, null), NO_WRITES));
        assertTrue(error.getMessage().contains("UNRESOLVED"), error.getMessage());
    }

    @Test void invalidModelShapeFailsClosed() throws Exception {
        var runner = runner(Map.of());
        var error = assertThrows(DirectoryExecutionException.class, () -> runner.recognizeDirectory(
                new ModelContext(Map.of("status", 1, "orderDetailList", "not a list", "payInfo", Map.of())), NO_WRITES));
        assertTrue(error.getMessage().contains("ERROR"), error.getMessage());
    }

    @Test void recognitionFollowsChangedDesignPredicateWithoutApplicationChanges() throws Exception {
        var runner = runner(Map.of("systems-order.xml", Map.of(
                "status = 1&#10;and&#10;every(orderDetailList, status = 1)",
                "status = 7&#10;and&#10;every(orderDetailList, status = 7)")));
        assertEquals("ordered", runner.recognizeDirectory(model(7, 7, null), NO_WRITES));
        assertThrows(DirectoryExecutionException.class, () -> runner.recognizeDirectory(model(1, 1, null), NO_WRITES));
    }

    @Test void singleStepRejectsRepeatsSkipsAndUndeclaredBack() throws Exception {
        var runner = runner(Map.of());
        runner.requireSingleStep(null, "ordered", false);
        runner.requireSingleStep("ordered", "paying", false);
        runner.requireSingleStep("paying", "PayResult", false);
        runner.requireSingleStep("success", "paying", true);
        runner.requireSingleStep("error", "paying", true);
        assertThrows(DirectoryExecutionException.class, () -> runner.requireSingleStep(null, "paying", false));
        assertThrows(DirectoryExecutionException.class, () -> runner.requireSingleStep("ordered", "PayResult", false));
        assertThrows(DirectoryExecutionException.class, () -> runner.requireSingleStep("paying", "paying", false));
        assertThrows(DirectoryExecutionException.class, () -> runner.requireSingleStep("paying", "paying", true));
        assertThrows(DirectoryExecutionException.class, () -> runner.requireSingleStep("paying", "ordered", true));
    }

    @Test void singleStepFollowsChangedDesignEdge() throws Exception {
        var runner = runner(Map.of("business-order-payment.xml", Map.of(
                "<subdirectory rel=\"ordered\"/>", "<subdirectory rel=\"awaiting\"/>",
                "</directory-info>", "<directory name=\"awaiting\" information-ref=\"order.ordered\" model-ref=\"OrderInfo\">"
                        + "<subdirectory-info><subdirectory rel=\"ordered\"/></subdirectory-info></directory></directory-info>")));
        runner.requireSingleStep("ordered", "awaiting", false);
        runner.requireSingleStep("awaiting", "paying", false);
        assertThrows(DirectoryExecutionException.class, () -> runner.requireSingleStep("ordered", "paying", false));
    }

    private DirectoryWorkflowRunner runner(Map<String, Map<String, String>> changes) throws Exception {
        Path fixture = Path.of(getClass().getClassLoader().getResource("mix-xml").toURI());
        try (var files = Files.list(fixture)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".xml")).toList()) {
                String source = Files.readString(file);
                for (var edit : changes.getOrDefault(file.getFileName().toString(), Map.of()).entrySet()) {
                    assertTrue(source.contains(edit.getKey()), "Fixture edit must apply");
                    source = source.replaceFirst(java.util.regex.Pattern.quote(edit.getKey()),
                            java.util.regex.Matcher.quoteReplacement(edit.getValue()));
                }
                Files.writeString(input.resolve(file.getFileName()), source);
            }
        }
        var custom = CustomActionRegistry.from(List.of(new CustomAction() {
            public String name() { return "smsNotify"; }
            public void execute(ActionExecutionContext context) { throw new AssertionError("No Actions during recognition"); }
        }));
        return new DirectoryWorkflowRunner(new DecXmlParser().parse(input), custom);
    }

    private static ModelContext model(int status, int detailStatus, Integer result) {
        var payment = new LinkedHashMap<String, Object>();
        if (result != null) { payment.put("id", 10); payment.put("resultCode", result); }
        return new ModelContext(Map.of("status", status,
                "orderDetailList", List.of(Map.of("status", detailStatus)), "payInfo", payment));
    }
}
