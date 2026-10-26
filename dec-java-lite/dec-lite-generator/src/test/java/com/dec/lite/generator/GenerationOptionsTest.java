package com.dec.lite.generator;

import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenerationOptionsTest {
    @TempDir Path temp;

    @Test void previewShowsRegroupingChangesAndLeavesAllOutputUntouched() throws Exception {
        var generator = new GeneratorPipeline(); var project = design(); var output = temp.resolve("preview");
        generator.generate(project, output, "example.app");
        Files.writeString(output.resolve("handwritten.txt"), "keep");
        var before = snapshot(output);
        var options = new GenerationOptions(Map.of("API-LIST", "Queries", "API-START", "Payments"));
        var plan = generator.preview(project, output, "example.app", options);
        assertTrue(plan.deleted().contains("src/main/java/example/app/order/service/OrdersService.java"));
        assertTrue(plan.created().contains("src/main/java/example/app/order/service/PaymentsService.java"));
        assertTrue(plan.updated().contains("DEC-DESIGN-INDEX.md"));
        assertTrue(plan.unchanged().contains("pom.xml"));
        assertEquals(List.of("handwritten.txt"), plan.preserved());
        assertTrue(plan.conflicts().isEmpty());
        assertEquals(before, snapshot(output));
        generator.generate(project, output, "example.app", options);
        assertTrue(generator.preview(project, output, "example.app", options).updated().isEmpty());
    }

    @Test void demoSubresourceOverridePreservesAllDtosAndDesignProjections() throws Exception {
        Path demo = Path.of("..", "..", "demo");
        var project = new DecYamlParser().parse(demo.resolve("order-payment-yaml"));
        var generator = new GeneratorPipeline();
        Path baseline = temp.resolve("baseline"), configured = temp.resolve("configured");
        var files = generator.generate(project, baseline, "com.example.orderpayment").files();
        var options = GenerationOptions.load(demo.resolve("order-payment-generation.properties"));
        generator.generate(project, configured, "com.example.orderpayment", options);
        for (String file : files) {
            if (file.contains("/controller/") || file.contains("/service/") || file.equals("DEC-DESIGN-INDEX.md")) continue;
            assertArrayEquals(Files.readAllBytes(baseline.resolve(file)), Files.readAllBytes(configured.resolve(file)), file);
        }
        assertEquals(routes(baseline), routes(configured));
        assertTrue(Files.exists(configured.resolve("src/main/java/com/example/orderpayment/order/controller/OrderPaymentsController.java")));
    }

    @Test void splitsSubresourcesAndMergesCapabilitiesWithoutChangingApiContractOrSystem() throws Exception {
        var project = design();
        var generator = new GeneratorPipeline();
        Path defaults = temp.resolve("default"), configured = temp.resolve("configured");
        generator.generate(project, defaults, "example.app");
        var options = new GenerationOptions(Map.of("API-START", "Payments", "API-AUDIT", "Payments"));
        generator.generate(project, configured, "example.app", options);
        Path java = configured.resolve("src/main/java/example/app");
        String payment = Files.readString(java.resolve("order/controller/PaymentsController.java"));
        assertTrue(payment.contains("startPayment("));
        assertTrue(payment.contains("readAudit("));
        assertTrue(payment.contains("API-START"));
        assertTrue(payment.contains("API-AUDIT"));
        assertFalse(payment.contains("listOrders("));
        assertTrue(Files.readString(java.resolve("order/controller/OrdersController.java")).contains("listOrders("));
        assertTrue(Files.readString(java.resolve("archive/controller/OrdersController.java")).contains("archive("));
        assertTrue(Files.exists(java.resolve("order/service/PaymentsService.java")));
        assertFalse(Files.exists(java.resolve("order/controller/AuditsController.java")));
        assertEquals(routes(defaults), routes(configured), "Grouping must not modify any endpoint mapping");
        String oldMethod = "@PathVariable(\"id\") @NotNull(message = \"id不能为空\") Long id";
        assertTrue(payment.contains(oldMethod));
        String index = Files.readString(configured.resolve("DEC-DESIGN-INDEX.md"));
        assertTrue(index.lines().filter(line -> line.startsWith("| `API-START`")).findFirst().orElseThrow().contains("PaymentsController.java"));
        assertEquals(options, GenerationOptions.load(configured.resolve("dec-generation-options.properties")));
        assertFalse(Files.exists(defaults.resolve("dec-generation-options.properties")));
    }

    @Test void regenerationCleansOldGroupsButPreservesHandwrittenFilesAndIsDeterministic() throws Exception {
        var project = design(); var generator = new GeneratorPipeline(); Path output = temp.resolve("out");
        generator.generate(project, output, "example.app");
        Files.writeString(output.resolve("handwritten.txt"), "keep");
        var first = new GenerationOptions(Map.of("API-LIST", "Queries", "API-START", "Payments"));
        generator.generate(project, output, "example.app", first);
        assertFalse(Files.exists(output.resolve("src/main/java/example/app/order/controller/OrdersController.java")));
        String controller = Files.readString(output.resolve("src/main/java/example/app/order/controller/PaymentsController.java"));
        String manifest = Files.readString(output.resolve("dec-generated-manifest.txt"));
        String configuration = Files.readString(output.resolve("dec-generation-options.properties"));
        var reversed = new LinkedHashMap<String, String>(); reversed.put("API-START", "Payments"); reversed.put("API-LIST", "Queries");
        generator.generate(project, output, "example.app", new GenerationOptions(reversed));
        assertEquals(controller, Files.readString(output.resolve("src/main/java/example/app/order/controller/PaymentsController.java")));
        assertEquals(manifest, Files.readString(output.resolve("dec-generated-manifest.txt")));
        assertEquals(configuration, Files.readString(output.resolve("dec-generation-options.properties")));
        generator.generate(project, output, "example.app");
        assertFalse(Files.exists(output.resolve("src/main/java/example/app/order/controller/PaymentsController.java")));
        assertFalse(Files.exists(output.resolve("dec-generation-options.properties")));
        assertEquals("keep", Files.readString(output.resolve("handwritten.txt")));
    }

    @Test void invalidIdsAndImplicitExplicitCollisionsFailBeforeTouchingOwnedOutput() throws Exception {
        var project = design(); var generator = new GeneratorPipeline(); Path output = temp.resolve("out");
        generator.generate(project, output, "example.app");
        String manifest = Files.readString(output.resolve("dec-generated-manifest.txt"));
        for (var mapping : List.of(Map.of("API-TYPO", "Payments"), Map.of("API-START", "Orders"))) {
            assertThrows(GenerationException.class, () -> generator.generate(project, output, "example.app", new GenerationOptions(mapping)));
            assertEquals(manifest, Files.readString(output.resolve("dec-generated-manifest.txt")));
            assertTrue(Files.exists(output.resolve("src/main/java/example/app/order/controller/OrdersController.java")));
        }
        // Intentional merge requires every member of the colliding class to opt in.
        generator.generate(project, output, "example.app", new GenerationOptions(Map.of("API-LIST", "Orders", "API-START", "Orders")));
    }

    @Test void regroupingRejectsEveryUnmanagedCollisionWithoutChangingAnyOutput() throws Exception {
        Path demo = Path.of("..", "..", "demo");
        var project = new DecYamlParser().parse(demo.resolve("order-payment-yaml"));
        var generator = new GeneratorPipeline();
        Path output = temp.resolve("collision");
        generator.generate(project, output, "com.example.orderpayment");
        String service = "src/main/java/com/example/orderpayment/order/service/OrderPaymentsService.java";
        String controller = "src/main/java/com/example/orderpayment/order/controller/OrderPaymentsController.java";
        Files.writeString(output.resolve(service), "handwritten service");
        Files.writeString(output.resolve(controller), "handwritten controller");
        var before = snapshot(output);
        var options = GenerationOptions.load(demo.resolve("order-payment-generation.properties"));
        var failure = assertThrows(GenerationException.class,
                () -> generator.generate(project, output, "com.example.orderpayment", options));
        assertTrue(failure.getMessage().contains(service));
        assertTrue(failure.getMessage().contains(controller));
        assertEquals(before, snapshot(output));
    }

    @Test void lateOutputObstructionLeavesOldGenerationAndManifestIntact() throws Exception {
        var project = design(); var generator = new GeneratorPipeline(); Path output = temp.resolve("obstructed");
        generator.generate(project, output, "example.app");
        // This path is emitted after the Java templates, so eager cleanup/writes lose old output.
        Files.createDirectory(output.resolve("dec-generation-options.properties"));
        Files.writeString(output.resolve("dec-generation-options.properties/keep.txt"), "keep");
        var before = snapshot(output);
        assertThrows(GenerationException.class, () -> generator.generate(project, output, "example.app",
                new GenerationOptions(Map.of("API-START", "Payments"))));
        assertEquals(before, snapshot(output));
    }

    private Map<String, String> snapshot(Path output) throws Exception {
        Map<String, String> result = new java.util.TreeMap<>();
        try (var paths = Files.walk(output)) {
            for (Path file : paths.filter(Files::isRegularFile).toList())
                result.put(output.relativize(file).toString(), Files.readString(file));
        }
        return result;
    }

    @Test void rejectsMethodCollisionInsideConfiguredGroup() throws Exception {
        var project = design();
        Path input = temp.resolve("api.yaml");
        Files.writeString(input, Files.readString(input).replace("name: readAudit", "name: listOrders"));
        project = new DecYamlParser().parse(input);
        var finalProject = project;
        assertThrows(GenerationException.class, () -> new GeneratorPipeline().generate(finalProject, temp.resolve("out"), "example.app",
                new GenerationOptions(Map.of("API-LIST", "Operations", "API-AUDIT", "Operations"))));
        assertFalse(Files.exists(temp.resolve("out")));
    }

    @Test void explicitGroupWorksForRootEndpointWithoutGuessingResource() throws Exception {
        Path input = temp.resolve("root.yaml");
        Files.writeString(input, "kind: api\nversion: dec/v1\napis:\n  - {id: API-ROOT, name: home, system: web, url: /, method: GET}\n");
        var project = new DecYamlParser().parse(input);
        new GeneratorPipeline().generate(project, temp.resolve("out"), "example.app", new GenerationOptions(Map.of("API-ROOT", "Home")));
        assertTrue(Files.readString(temp.resolve("out/src/main/java/example/app/web/controller/HomeController.java")).contains("path = \"/\""));
    }

    @Test void configParsingRejectsUnknownDuplicateInvalidAndUnsupportedVersionValues() throws Exception {
        for (String text : List.of("", "version=dec-generation/v2", "version=dec-generation/v1\napi.groups.API-START=Payments",
                "version=dec-generation/v1\napi.group.API-START=One\napi.group.API-START=Two",
                "version=dec-generation/v1\napi.group.API-START=../Escape", "version=dec-generation/v1\napi.group.API-START=bad-name",
                "version=dec-generation/v1\napi.group.API-START=class", "version=dec-generation/v1\napi.group.API-START=",
                "version=dec-generation/v1\nversion=dec-generation/v1")) {
            Path config = temp.resolve("generation.properties"); Files.writeString(config, text);
            assertThrows(GenerationException.class, () -> GenerationOptions.load(config), text);
        }
        Path config = temp.resolve("valid.properties");
        Files.writeString(config, "# 子资源\nversion=dec-generation/v1\napi.group.API-START=Payments\n");
        var options = GenerationOptions.load(config);
        assertEquals("Payments", options.apiGroups().get("API-START"));
        assertThrows(UnsupportedOperationException.class, () -> options.apiGroups().put("API-LIST", "Other"));
    }

    private DecProject design() throws Exception {
        Path input = temp.resolve("api.yaml");
        Files.writeString(input, """
                kind: api
                version: dec/v1
                apis:
                  - {id: API-LIST, name: listOrders, system: order, url: /api/orders, method: GET}
                  - id: API-START
                    name: startPayment
                    system: order
                    url: /api/orders/{id}/payment
                    method: POST
                    request:
                      params:
                        - {id: PARAM-ID, name: id, in: path, type: long, required: true}
                  - {id: API-AUDIT, name: readAudit, system: order, url: /api/audits, method: GET}
                  - {id: API-ARCHIVE, name: archive, system: archive, url: /api/orders, method: GET}
                """);
        return new DecYamlParser().parse(input);
    }
    private List<String> routes(Path output) throws Exception {
        var routes = new java.util.ArrayList<String>();
        try (var paths = Files.walk(output.resolve("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith("Controller.java")).toList())
                routes.addAll(Files.readAllLines(path).stream().filter(line -> line.contains("@RequestMapping(path")).toList());
        }
        return routes.stream().sorted().toList();
    }
}
