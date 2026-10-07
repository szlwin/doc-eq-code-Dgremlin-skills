package com.dec.lite.generator;

import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorPipelineTest {
    @Test void checkedInDemoApplicationMatchesItsCanonicalYaml() throws Exception {
        Path demo = Path.of("..", "..", "demo").toAbsolutePath().normalize();
        Path checked = demo.resolve("order-payment-app");
        Path fresh = Files.createTempDirectory("dec-demo-generated-").resolve("project");
        new GeneratorPipeline().generate(new DecYamlParser().parse(demo.resolve("order-payment-yaml")),
                fresh, "com.example.orderpayment");
        for (String relative : Files.readAllLines(fresh.resolve("dec-generated-manifest.txt"))) {
            assertEquals(Files.readString(fresh.resolve(relative)), Files.readString(checked.resolve(relative)),
                    "demo generated file drifted: " + relative);
        }
        assertEquals(Files.readString(fresh.resolve("dec-generated-manifest.txt")),
                Files.readString(checked.resolve("dec-generated-manifest.txt")));
    }
    @Test void checkedInMixExampleMatchesCanonicalGeneratorOutput() throws Exception {
        Path input = Path.of(getClass().getClassLoader().getResource("mix").toURI());
        Path checked = Path.of("..", "examples", "order-service", "generated").toAbsolutePath().normalize();
        Path fresh = Files.createTempDirectory("dec-mix-generated-").resolve("project");
        new GeneratorPipeline().generate(new DecYamlParser().parse(input), fresh, "com.example.order");
        assertEquals(snapshot(fresh), snapshot(checked), "generated example drifted from fixtures/mix");
    }
    @Test
    void generatesDeterministicSpringProjectFromCanonicalDocuments() throws Exception {
        Path input = Files.createTempDirectory("dec-generator-input-");
        Files.writeString(input.resolve("data.yaml"), """
                kind: data
                version: dec/v1
                datas:
                  - id: DATA-ORDER
                    name: order
                    properties:
                      id: {type: long}
                      remark: {type: string}
                    tables: []
                """);
        Files.writeString(input.resolve("view.yaml"), """
                kind: view
                version: dec/v1
                views:
                  - id: VIEW-ORDER-INFO
                    name: OrderInfo
                    targetMain: order
                    properties:
                      id: {ref: id}
                      remark: {ref: remark}
                """);
        Files.writeString(input.resolve("api.yaml"), """
                kind: api
                version: dec/v1
                apis:
                  - id: API-ORDER-SUBMIT
                    name: submitOrder
                    system: order
                    url: /orders
                    method: POST
                    request:
                      params:
                        - id: API-PARAM-REMARK
                          name: remark
                          in: body
                          type: string
                          required: true
                    response:
                      modelRef: OrderInfo
                """);

        DecProject project = new DecYamlParser().parse(input);
        Path output = Files.createTempDirectory("dec-generator-output-").resolve("project");
        GeneratorPipeline.GenerationResult result = new GeneratorPipeline().generate(project, output, "com.example.generated");

        assertEquals(10, result.files().size());
        assertTrue(Files.exists(output.resolve(".dec-generated")));
        assertTrue(Files.exists(output.resolve("pom.xml")));
        assertTrue(Files.exists(output.resolve("src/main/java/com/example/generated/controller/SubmitOrderController.java")));
        String controller = Files.readString(output.resolve("src/main/java/com/example/generated/controller/SubmitOrderController.java"));
        assertTrue(controller.contains("@RequestMapping(path = \"/orders\", method = RequestMethod.POST)"));
        assertTrue(controller.contains("return service.submitOrder(request);"));
        String service = Files.readString(output.resolve("src/main/java/com/example/generated/service/SubmitOrderService.java"));
        assertTrue(service.contains("DESIGN_GAP API-ORDER-SUBMIT"));
        assertFalse(service.contains("return new OrderInfo()"));

        Map<Path, String> first = snapshot(output);
        new GeneratorPipeline().generate(project, output, "com.example.generated");
        assertEquals(first, snapshot(output));
    }

    @Test
    void refusesToOverwriteDirectoryWithoutGeneratorMarker() throws Exception {
        Path input = Files.createTempDirectory("dec-generator-input-");
        Files.writeString(input.resolve("api.yaml"), """
                kind: api
                version: dec/v1
                apis:
                  - id: API-ORDER-SUBMIT
                    name: submitOrder
                    system: order
                    url: /orders
                    method: POST
                """);
        Path output = Files.createTempDirectory("dec-generator-output-");
        Files.writeString(output.resolve("manual.txt"), "keep me");
        DecProject project = new DecYamlParser().parse(input);
        assertThrows(GenerationException.class, () -> new GeneratorPipeline().generate(project, output));
        assertEquals("keep me", Files.readString(output.resolve("manual.txt")));
    }
    @Test void rejectsUnsafeOutputPackageAndSymbolicLinks() throws Exception {
        Path input = Files.createTempDirectory("dec-generator-safe-input-");
        Files.writeString(input.resolve("data.yaml"), "kind: data\nversion: dec/v1\ndatas: [{id: DATA-X, name: x, properties: {}, tables: []}]\n");
        DecProject project = new DecYamlParser().parse(input);
        Path root = Files.createTempDirectory("dec-generator-safe-output-");
        assertThrows(GenerationException.class,
                () -> new GeneratorPipeline().generate(project, root.resolve("project"), "com.example/../../outside"));
        Path outside = Files.createTempDirectory("dec-generator-outside-");
        Path link = root.resolve("linked");
        Files.createSymbolicLink(link, outside);
        assertThrows(GenerationException.class,
                () -> new GeneratorPipeline().generate(project, link, "com.example.safe"));
        assertThrows(GenerationException.class,
                () -> new GeneratorPipeline().generate(project, link.resolve("nested"), "com.example.safe"));
        assertFalse(Files.exists(outside.resolve("pom.xml")));
    }

    @Test void rejectsUnsafeManifestBeforeDeletingGeneratedFiles() throws Exception {
        Path input = Files.createTempDirectory("dec-generator-manifest-input-");
        Files.writeString(input.resolve("data.yaml"), "kind: data\nversion: dec/v1\ndatas: [{id: DATA-X, name: x, properties: {}, tables: []}]\n");
        DecProject project = new DecYamlParser().parse(input);
        Path root = Files.createTempDirectory("dec-generator-manifest-output-");
        Path output = root.resolve("project");
        new GeneratorPipeline().generate(project, output);
        Path sentinel = root.resolve("keep.txt");
        Files.writeString(sentinel, "keep");
        Files.writeString(output.resolve("dec-generated-manifest.txt"), "../keep.txt\n");
        assertThrows(GenerationException.class, () -> new GeneratorPipeline().generate(project, output));
        assertEquals("keep", Files.readString(sentinel));
    }

    @Test
    void supportsP2ValidationEnumsRelationsAndDataSourceBinding() throws Exception {
        Path input = Files.createTempDirectory("dec-generator-p2-input-");
        Files.writeString(input.resolve("config.yaml"), """
                kind: config
                version: dec/v1
                dataSourceInfo:
                  dataSources:
                    - name: data1
                      type: MySQL
                """);
        Files.writeString(input.resolve("enum.yaml"), """
                kind: enum
                version: dec/v1
                enums:
                  - id: ENUM-ORDER-STATUS
                    name: OrderStatus
                    values:
                      - {id: ENUM-VALUE-WAIT, value: 1, name: WAIT_PAY}
                      - {id: ENUM-VALUE-SUCCESS, value: 2, name: PAY_SUCCESS}
                """);
        Files.writeString(input.resolve("data.yaml"), """
                kind: data
                version: dec/v1
                datas:
                  - id: DATA-ORDER
                    name: order
                    properties:
                      id: {type: long}
                      status: {type: int}
                    tables:
                      - name: order_info
                        dataSource: data1
                        key: id
                        keyType: increment
                        columns:
                          id: id
                          status: {ref: status, relEnum: OrderStatus}
                  - id: DATA-ITEM
                    name: item
                    properties:
                      id: {type: long}
                      orderId: {type: long}
                    tables: []
                """);
        Files.writeString(input.resolve("view.yaml"), """
                kind: view
                version: dec/v1
                views:
                  - id: VIEW-ORDER-INFO
                    name: OrderInfo
                    targetMain: order
                    properties:
                      id: {ref: id}
                      status: {ref: status}
                      items:
                        relation: one-to-many
                        data: item
                        key: orderId
                        relKey: id
                        properties:
                          id: {ref: id}
                          orderId: {ref: orderId}
                """);
        Files.writeString(input.resolve("api.yaml"), """
                kind: api
                version: dec/v1
                apis:
                  - id: API-ORDER-SUBMIT
                    name: submitOrder
                    system: order
                    url: /orders
                    method: POST
                    request:
                      params:
                        - id: API-PARAM-STATUS
                          name: status
                          in: body
                          type: int
                          required: true
                          relEnum: OrderStatus
                        - id: API-PARAM-TYPE
                          name: type
                          in: body
                          type: int
                          required: true
                          validations:
                            - type: enum
                              values: [1, 2]
                      validations:
                        - type: expression
                          expression: status = 1 and type = 2
                          message: status/type combo invalid
                    response:
                      modelRef: OrderInfo
                """);

        DecProject project = new DecYamlParser().parse(input);
        Path output = Files.createTempDirectory("dec-generator-p2-output-").resolve("project");
        new GeneratorPipeline().generate(project, output, "com.example.p2");

        String request = Files.readString(output.resolve("src/main/java/com/example/p2/dto/SubmitOrderRequest.java"));
        assertTrue(request.contains("@AssertTrue"));
        assertTrue(request.contains("status.getValue()"));
        assertTrue(request.contains("Objects.equals(this.type, 1)"));
        String view = Files.readString(output.resolve("src/main/java/com/example/p2/view/OrderInfo.java"));
        assertTrue(view.contains("List<OrderInfoItems> items"));
        String relationView = Files.readString(output.resolve("src/main/java/com/example/p2/view/OrderInfoItems.java"));
        assertTrue(relationView.contains("private Long orderId;"));
        assertTrue(Files.readString(output.resolve("src/main/resources/dec/datasources.properties")).contains("data1.type=MySQL"));
    }

    private static Map<Path, String> snapshot(Path root) throws Exception {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> !root.relativize(path).toString().startsWith("target/"))
                    .filter(path -> !path.getFileName().toString().equals(".DS_Store"))
                    .collect(java.util.stream.Collectors.toMap(root::relativize, path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }));
        }
    }
}
