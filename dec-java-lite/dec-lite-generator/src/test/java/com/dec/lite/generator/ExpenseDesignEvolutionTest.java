package com.dec.lite.generator;

import com.dec.lite.action.*;
import com.dec.lite.directory.*;
import com.dec.lite.information.ModelContext;
import com.dec.lite.model.*;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Compile generated design projections and execute the same changed design through the real runtime. */
class ExpenseDesignEvolutionTest {
    @TempDir Path temp;

    @Test void checkedInExpenseProjectionAndPackagedDesignMatchCanonicalSource() throws Exception {
        var project = project();
        Path output = temp.resolve("generated");
        new GeneratorPipeline().generate(project, output, "com.example.expenses");
        Path checked = Path.of("..", "examples", "expense-approval", "generated");
        for (String file : Files.readAllLines(output.resolve("dec-generated-manifest.txt")))
            assertEquals(Files.readString(output.resolve(file)), Files.readString(checked.resolve(file)), file);
        try (var loader = new URLClassLoader(new java.net.URL[]{checked.resolve("src/main/resources").toUri().toURL()}, null)) {
            assertEquals(project.getDesignIds(), new com.dec.lite.parser.DecXmlParser()
                    .parseClasspath(loader, "dec/design-files.txt").getDesignIds());
        }
    }

    @Test void changedInformationRegeneratesProjectionAndChangesEligibilityWithoutChangingApplication() throws Exception {
        var original = project();
        var tighter = variant(original, true);
        Path output = temp.resolve("generated");
        new GeneratorPipeline().generate(original, output, "example.expenses");
        String oldInfo = Files.readString(output.resolve("src/main/java/example/expenses/expense/information/ExpenseInformation.java"));
        Files.writeString(output.resolve("application-owned.txt"), "preserve");
        var before = execute(original, generatedTarget(output));
        assertEquals(List.of("recordSubmitted", "recordApproved"), before.calls);
        assertTrue(before.success);
        new GeneratorPipeline().generate(tighter, output, "example.expenses");
        String newInfo = Files.readString(output.resolve("src/main/java/example/expenses/expense/information/ExpenseInformation.java"));
        assertNotEquals(oldInfo, newInfo);
        assertTrue(newInfo.contains("amount <= 500"));
        assertEquals("preserve", Files.readString(output.resolve("application-owned.txt")));
        var after = execute(tighter, generatedTarget(output));
        assertFalse(after.success);
        assertEquals(List.of("recordSubmitted"), after.calls);
        assertTrue(execute(original, "approved").success, "A changed design must not mutate another version's compilation");
    }

    @Test void removingIntermediateDirectoryRegeneratesFlowAndChangesExecutedActions() throws Exception {
        var original = project();
        var shorter = variant(original, false);
        Path output = temp.resolve("generated");
        new GeneratorPipeline().generate(original, output, "example.expenses");
        assertTrue(Files.readString(output.resolve("src/main/java/example/expenses/business/ExpenseApprovalFlow.java"))
                .contains("DirectoryNode SUBMITTED"));
        var originalRunner = runner(original, new ArrayList<>());
        assertThrows(DirectoryExecutionException.class, () -> originalRunner.requireSingleStep("draft", "approved", false));
        new GeneratorPipeline().generate(shorter, output, "example.expenses");
        String flow = Files.readString(output.resolve("src/main/java/example/expenses/business/ExpenseApprovalFlow.java"));
        assertFalse(flow.contains("DirectoryNode SUBMITTED"));
        assertTrue(flow.contains("new DirectoryEdge(\"draft\""));
        runner(shorter, new ArrayList<>()).requireSingleStep("draft", "approved", false);
        var result = execute(shorter, generatedTarget(output));
        assertTrue(result.success);
        assertEquals(List.of("recordApproved"), result.calls);
    }

    private String generatedTarget(Path output) throws Exception {
        var classes = Files.createTempDirectory(temp, "projection-classes-");
        List<String> args = new ArrayList<>(List.of("-d", classes.toString()));
        try (var paths = Files.walk(output.resolve("src/main/java"))) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/design/") || p.toString().contains("/business/") || p.toString().contains("/information/"))
                    .forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, null)) {
            var flow = loader.loadClass("example.expenses.business.ExpenseApprovalFlow");
            var node = flow.getField("APPROVED").get(null);
            return (String) node.getClass().getMethod("name").invoke(node);
        }
    }

    private Outcome execute(DecProject project, String target) {
        List<String> calls = new ArrayList<>();
        var actions = actions(calls);
        var runner = new DirectoryWorkflowRunner(project, actions);
        var model = new ModelContext(Map.of("id", 1, "status", 0, "amount", 800, "reviewerEnabled", true));
        var transaction = WorkflowTransaction.externallyManaged(() -> new WorkflowTransaction.ExternalTransaction() {
            public boolean isActive() { return true; }
            public boolean isRollbackOnly() { return false; }
            public void markRollbackOnly() { }
        });
        try {
            var result = runner.run(new DirectoryWorkflowRunner.RunRequest("draft", target, false, model, Map.of(),
                    (rule, invocation) -> { throw new AssertionError("No RuleView in approval fixture"); }, actions, transaction, Instant.MAX));
            assertEquals("approved", result.execution().currentDirectory());
            return new Outcome(true, calls);
        } catch (DirectoryWorkflowException error) {
            assertEquals(com.dec.lite.runtime.RuntimeErrorCode.DEPENDENCY, error.execution().errorDetail().code());
            return new Outcome(false, calls);
        }
    }
    private DirectoryWorkflowRunner runner(DecProject project, List<String> calls) { return new DirectoryWorkflowRunner(project, actions(calls)); }
    private CustomActionRegistry actions(List<String> calls) {
        return CustomActionRegistry.from(List.of("recordSubmitted", "recordApproved").stream().map(name -> new CustomAction() {
            public String name() { return name; }
            public void execute(ActionExecutionContext context) { calls.add(name); }
        }).toList());
    }
    private DecProject project() throws Exception {
        return new DecYamlParser().parse(Path.of(getClass().getClassLoader().getResource("expense-approval").toURI()));
    }
    private DecProject variant(DecProject source, boolean tighter) throws Exception {
        var directory = Files.createTempDirectory(temp, "design-revision-");
        for (var doc : source.getDocuments()) {
            Map<String, Object> root = copy(doc.root());
            if (tighter && doc.kind() == DecKind.SYSTEMS) {
                for (var system : list(root.get("systems"))) for (var info : list(system.get("information")))
                    if ("eligible".equals(info.get("name"))) info.put("ruleData", "amount <= 500 and reviewerEnabled = true");
            }
            if (!tighter && doc.kind() == DecKind.BUSINESS) {
                var business = (Map<String, Object>) root.get("business");
                var directories = list(business.get("directories"));
                directories.removeIf(node -> "submitted".equals(node.get("name")));
                directories.stream().filter(node -> "approved".equals(node.get("name"))).findFirst().orElseThrow()
                        .put("subDirectories", List.of(Map.of("rel", "draft")));
            }
            Files.writeString(directory.resolve(doc.source().getFileName()), new org.yaml.snakeyaml.Yaml().dump(root));
        }
        return new DecYamlParser().parse(directory);
    }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> list(Object value) { return (List<Map<String, Object>>) value; }
    @SuppressWarnings("unchecked") private static <T> T copy(T value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> result.put((String) key, copy(child)));
            return (T) result;
        }
        if (value instanceof List<?> list) return (T) new ArrayList<>(list.stream().map(ExpenseDesignEvolutionTest::copy).toList());
        return value;
    }
    private record Outcome(boolean success, List<String> calls) { }
}
