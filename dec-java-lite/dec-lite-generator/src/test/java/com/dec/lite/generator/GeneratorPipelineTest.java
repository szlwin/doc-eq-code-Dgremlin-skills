package com.dec.lite.generator;

import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorPipelineTest {
    @Test void groupsOperationsAcrossFilesButSeparatesSystemsAndResources() throws Exception {
        Path input = Files.createTempDirectory("dec-api-groups-");
        Files.writeString(input.resolve("a.yaml"), """
                kind: api
                version: dec/v1
                apis:
                  - id: API-GET
                    name: getOrder
                    system: order
                    url: /api/v1/orders/{id}
                    method: GET
                    request:
                      params:
                        - {id: PARAM-GET, name: id, in: path, type: long, required: true}
                  - {id: API-ITEMS, name: listItems, system: order, url: /api/items, method: GET}
                """);
        Files.writeString(input.resolve("b.yaml"), """
                kind: api
                version: dec/v1
                apis:
                  - {id: API-SUBMIT, name: submitOrder, system: order, url: /api/orders/submit, method: POST}
                  - id: API-ARCHIVE
                    name: getOrder
                    system: archive
                    url: /api/orders/{id}
                    method: GET
                    request:
                      params:
                        - {id: PARAM-ARCHIVE, name: id, in: path, type: long, required: true}
                """);
        Path output = Files.createTempDirectory("dec-api-groups-output-").resolve("project");
        new GeneratorPipeline().generate(new DecYamlParser().parse(input), output, "com.example.grouped");
        Path java = output.resolve("src/main/java/com/example/grouped");
        String controller = Files.readString(java.resolve("order/controller/OrdersController.java"));
        assertTrue(controller.contains("void getOrder("));
        assertTrue(controller.contains("void submitOrder("));
        assertTrue(controller.contains("YAML a.yaml#apis[id=API-GET]"));
        assertTrue(controller.contains("YAML b.yaml#apis[id=API-SUBMIT]"));
        assertTrue(controller.contains("/api/v1/orders/{id}"));
        String service = Files.readString(java.resolve("order/service/OrdersService.java"));
        assertTrue(service.contains("void getOrder("));
        assertTrue(service.contains("void submitOrder("));
        assertTrue(Files.exists(java.resolve("order/controller/ItemsController.java")));
        assertTrue(Files.exists(java.resolve("archive/controller/OrdersController.java")));
        assertFalse(Files.exists(java.resolve("order/controller/GetOrderController.java")));
        assertFalse(controller.contains("listItems("));
    }

    @Test void rejectsCollidingGroupedMethodsBeforeReplacingOutput() throws Exception {
        Path input = Files.createTempDirectory("dec-api-collision-");
        Files.writeString(input.resolve("api.yaml"), """
                kind: api
                version: dec/v1
                apis:
                  - {id: API-A, name: get-order, system: order, url: /api/orders/a, method: GET}
                  - {id: API-B, name: get_order, system: order, url: /api/orders/b, method: GET}
                """);
        Path output = Files.createTempDirectory("dec-api-collision-output-");
        Files.writeString(output.resolve("keep.txt"), "untouched");
        GenerationException failure = assertThrows(GenerationException.class, () ->
                new GeneratorPipeline().generate(new DecYamlParser().parse(input), output));
        assertTrue(failure.getMessage().contains("API methods collide"));
        assertEquals("untouched", Files.readString(output.resolve("keep.txt")));
    }


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
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{
                checked.resolve("src/main/resources").toUri().toURL()}, null)) {
            var packaged = new com.dec.lite.parser.DecXmlParser().parseClasspath(loader, "dec/design-files.txt");
            assertEquals(new DecYamlParser().parse(demo.resolve("order-payment-yaml")).getDesignIds(),
                    packaged.getDesignIds(), "Manifest must load the complete packaged design");
        }
    }

    @Test void ambiguousPackagedFilenamesFailBeforeDeletingPriorOutput() throws Exception {
        var project = new DecProject();
        for (String folder : List.of("a", "b")) project.addDocument(new com.dec.lite.model.DecDocument(
                com.dec.lite.model.DecKind.CONFIG, "dec/v1", Path.of(folder, "config.yaml"), Map.of(), List.of()));
        Path output = Files.createTempDirectory("dec-duplicate-resources-");
        Files.writeString(output.resolve(".dec-generated"), "owned");
        Files.writeString(output.resolve("dec-generated-manifest.txt"), "keep.txt\n");
        Files.writeString(output.resolve("keep.txt"), "prior generation");
        var failure = assertThrows(GenerationException.class, () -> new GeneratorPipeline().generate(project, output));
        assertTrue(failure.getMessage().contains("duplicate packaged design filename"));
        assertEquals("prior generation", Files.readString(output.resolve("keep.txt")));
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

        assertEquals(14, result.files().size());
        String index = Files.readString(output.resolve("DEC-DESIGN-INDEX.md"));
        assertTrue(index.contains("| `DATA-ORDER` | `data.yaml#datas[id=DATA-ORDER]` | `data.xml#datas[id=DATA-ORDER]` | data |"));
        assertTrue(index.contains("[OrderDao.java](src/main/java/com/example/generated/common/dao/OrderDao.java)"));
        assertTrue(index.contains("| `VIEW-ORDER-INFO` | `view.yaml#views[id=VIEW-ORDER-INFO]` | `view.xml#views[id=VIEW-ORDER-INFO]` | view |"));
        assertTrue(index.contains("| `API-ORDER-SUBMIT` | `api.yaml#apis[id=API-ORDER-SUBMIT]` | `api.xml#apis[id=API-ORDER-SUBMIT]` | api |"));
        assertTrue(Files.exists(output.resolve(".dec-generated")));
        assertTrue(Files.exists(output.resolve("pom.xml")));
        assertTrue(Files.exists(output.resolve("src/main/java/com/example/generated/order/controller/OrdersController.java")));
        String controller = Files.readString(output.resolve("src/main/java/com/example/generated/order/controller/OrdersController.java"));
        assertTrue(controller.contains("@RequestMapping(path = \"/orders\", method = RequestMethod.POST)"));
        assertTrue(controller.contains("return service.submitOrder(request);"));
        assertTrue(controller.contains("YAML api.yaml#apis[id=API-ORDER-SUBMIT]"));
        assertTrue(controller.contains("XML api.xml api[name=submitOrder]"));
        assertFalse(controller.contains("synchronized"), "generated controllers must not own request locks");
        assertFalse(controller.contains("import org.springframework.web.bind.annotation.RequestHeader;"));
        assertFalse(controller.contains("import org.springframework.web.bind.annotation.PathVariable;"));
        assertFalse(controller.contains(".view.*;"));
        String service = Files.readString(output.resolve("src/main/java/com/example/generated/order/service/OrdersService.java"));
        assertTrue(service.contains("public interface OrdersService"));
        assertTrue(service.contains("DEC API-ORDER-SUBMIT; YAML api.yaml#apis[id=API-ORDER-SUBMIT]"));
        assertTrue(service.contains("OrderInfo submitOrder(SubmitOrderRequest request);"));
        assertFalse(service.contains("synchronized"), "generated service seams must not own request locks");
        assertFalse(service.contains("UnsupportedOperationException"));
        assertFalse(service.contains("return new OrderInfo()"));
        String dao = Files.readString(output.resolve("src/main/java/com/example/generated/common/dao/OrderDao.java"));
        assertTrue(dao.contains("DEC DATA-ORDER; YAML data.yaml#datas[id=DATA-ORDER]"));
        assertTrue(dao.contains("XML data.xml data[name=order]"));
        assertTrue(dao.contains("extends CrudDao<Order>"));
        String mapper = Files.readString(output.resolve("src/main/java/com/example/generated/common/mapper/OrderMapper.java"));
        assertTrue(mapper.contains("extends CrudMapper<Order>"));

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

    @Test
    void emitsMessagesForGeneratedBeanValidationAnnotations() throws Exception {
        Path input = Files.createTempDirectory("dec-generator-validation-message-input-");
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
                        - id: API-PARAM-PRICE
                          name: productPrice
                          in: body
                          type: decimal
                          required: true
                          desc: 产品价格
                          validations:
                            - type: min
                              value: 1
                              message: 产品价格最小为1元
                """);
        Path output = Files.createTempDirectory("dec-generator-validation-message-output-").resolve("project");
        new GeneratorPipeline().generate(new DecYamlParser().parse(input), output, "com.example.validation");
        String request = Files.readString(output.resolve("src/main/java/com/example/validation/order/dto/SubmitOrderRequest.java"));
        assertTrue(request.contains("@NotNull(message = \"产品价格不能为空\")"));
        assertTrue(request.contains("@Min(value = 1, message = \"产品价格最小为1元\")"));
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

        String request = Files.readString(output.resolve("src/main/java/com/example/p2/order/dto/SubmitOrderRequest.java"));
        assertTrue(request.contains("@AssertTrue"));
        assertTrue(request.contains("status.getValue()"));
        assertTrue(request.contains("Objects.equals(this.type, 1)"));
        String view = Files.readString(output.resolve("src/main/java/com/example/p2/common/view/OrderInfo.java"));
        assertTrue(view.contains("List<OrderInfoItems> items"));
        String relationView = Files.readString(output.resolve("src/main/java/com/example/p2/common/view/OrderInfoItems.java"));
        assertTrue(relationView.contains("private Long orderId;"));
        assertTrue(Files.readString(output.resolve("src/main/resources/dec/datasources.properties")).contains("data1.type=MySQL"));
    }

    @Test
    void rejectsStringAndFloatingPointEnumValues() throws Exception {
        for (String value : List.of("\"1\"", "1.0")) {
            Path input = Files.createTempDirectory("dec-generator-enum-type-input-");
            Files.writeString(input.resolve("enum.yaml"), """
                    kind: enum
                    version: dec/v1
                    enums:
                      - id: ENUM-ORDER-STATUS
                        name: OrderStatus
                        values:
                          - id: ENUM-VALUE-WAIT
                            value: %s
                            name: WAIT_PAY
                    """.formatted(value));
            DecProject project = new DecYamlParser().parse(input);
            Path output = Files.createTempDirectory("dec-generator-enum-type-output-").resolve("project");
            assertThrows(GenerationException.class,
                    () -> new GeneratorPipeline().generate(project, output, "com.example.enumtypes"), value);
        }
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
