package com.dec.lite.parser;

import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecYamlParserTest {
    @Test
    void parsesAllCanonicalKindsAndIndexesNestedDesignIds() throws Exception {
        Path dir = Files.createTempDirectory("dec-java-lite-p0-");
        write(dir, "api.yaml", """
                kind: api
                version: dec/v1
                apis:
                  - id: API-ORDER-SUBMIT
                    name: submitOrder
                    system: order
                    url: /orders
                    method: POST
                """);
        write(dir, "data.yaml", """
                kind: data
                version: dec/v1
                datas:
                  - id: DATA-ORDER
                    name: order
                    properties: {}
                    tables: []
                """);
        write(dir, "view.yaml", """
                kind: view
                version: dec/v1
                views:
                  - id: VIEW-ORDER-INFO
                    name: OrderInfo
                    targetMain: order
                    properties: {}
                """);
        write(dir, "enum.yaml", """
                kind: enum
                version: dec/v1
                enums:
                  - id: ENUM-ORDER-STATUS
                    name: OrderStatus
                    values: []
                """);
        write(dir, "rule.yaml", """
                kind: rule
                version: dec/v1
                ruleViews:
                  - id: RV-ORDER-PAYABLE
                    name: payable
                    code: PAYABLE
                    rules: []
                """);
        write(dir, "systems.yaml", """
                kind: systems
                version: dec/v1
                systems:
                  - id: SYS-ORDER
                    name: order
                    information:
                      - id: INFO-ORDER-PAYABLE
                        name: payable
                """);
        write(dir, "business.yaml", """
                kind: business
                version: dec/v1
                business:
                  id: BUS-ORDER-PAYMENT
                  name: order-payment
                  directories:
                    - id: DIR-ORDERED
                      name: ordered
                      actions:
                        - id: ACT-SAVE-ORDER
                          name: saveOrder
                """);
        write(dir, "config.yaml", """
                kind: config
                version: dec/v1
                dataSourceInfo:
                  dataSources:
                    - name: data1
                      type: MySQL
                """);

        DecProject project = new DecYamlParser().parse(dir);
        assertEquals(8, project.getDocuments().size());
        assertEquals(Set.of(DecKind.API, DecKind.DATA, DecKind.VIEW, DecKind.ENUM,
                DecKind.RULE, DecKind.SYSTEMS, DecKind.BUSINESS, DecKind.CONFIG),
                project.getDocuments().stream().map(document -> document.kind()).collect(Collectors.toSet()));
        assertTrue(project.getDesignIds().containsAll(Set.of(
                "API-ORDER-SUBMIT", "DATA-ORDER", "VIEW-ORDER-INFO", "ENUM-ORDER-STATUS",
                "RV-ORDER-PAYABLE", "SYS-ORDER", "INFO-ORDER-PAYABLE", "BUS-ORDER-PAYMENT",
                "DIR-ORDERED", "ACT-SAVE-ORDER")));
        assertTrue(project.getDesignGaps().isEmpty(), "recognized runtime kinds are part of the canonical contract");
    }

    @Test
    void rejectsLegacySingletonRoot() throws Exception {
        Path file = Files.createTempFile("legacy-", ".yaml");
        Files.writeString(file, "api:\n  name: submitOrder\n");
        DecParseException error = assertThrows(DecParseException.class, () -> new DecYamlParser().parse(file));
        assertTrue(error.getMessage().contains("canonical DEC kind"));
    }

    @Test
    void rejectsHtmlRecoveryArtifact() throws Exception {
        Path file = Files.createTempFile("recovery-", ".yaml");
        Files.writeString(file, """
                artifact:
                  producer: html-recovery
                kind: api
                version: dec/v1
                apis: []
                """);
        DecParseException error = assertThrows(DecParseException.class, () -> new DecYamlParser().parse(file));
        assertTrue(error.getMessage().contains("HTML recovery artifact"));
    }

    @Test
    void rejectsDuplicateDesignIds() throws Exception {
        Path dir = Files.createTempDirectory("duplicate-");
        write(dir, "one.yaml", """
                kind: data
                version: dec/v1
                datas:
                  - id: DATA-SAME
                    name: one
                    properties: {}
                    tables: []
                """);
        write(dir, "two.yaml", """
                kind: view
                version: dec/v1
                views:
                  - id: DATA-SAME
                    name: two
                    targetMain: one
                    properties: {}
                """);
        DecParseException error = assertThrows(DecParseException.class, () -> new DecYamlParser().parse(dir));
        assertTrue(error.getMessage().contains("duplicate designId DATA-SAME"));
    }
    @Test void rejectsYamlObjectConstruction() throws Exception {
        Path file = Files.createTempFile("unsafe-yaml-", ".yaml");
        Files.writeString(file, "kind: config\nversion: dec/v1\ndataSourceInfo: !!java.lang.ProcessBuilder [sh]\n");
        assertThrows(DecParseException.class, () -> new DecYamlParser().parse(file));
    }
    @Test void rejectsXmlExternalEntitiesAndUnknownSemanticAttributes() throws Exception {
        Path file = Files.createTempFile("unsafe-xml-", ".xml");
        Files.writeString(file, "<!DOCTYPE systems [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><systems><system name='&secret;'/></systems>");
        assertThrows(DecParseException.class, () -> new DecXmlParser().parse(file));
        Files.writeString(file, "<business-config name='x'><directory-info><directory name='d' model-ref='M' information-ref='x.y' any-one='true'/></directory-info></business-config>");
        assertThrows(DecParseException.class, () -> new DecXmlParser().parse(file));
    }

    @Test
    void reportsMissingNestedActionDesignId() throws Exception {
        Path file = Files.createTempFile("missing-action-id-", ".yaml");
        Files.writeString(file, """
                kind: business
                version: dec/v1
                business:
                  id: BUS-ORDER
                  name: order
                  directories:
                    - id: DIR-CREATE
                      name: create
                      actions:
                        - name: saveOrder
                """);
        DecProject project = new DecYamlParser().parse(file);
        assertTrue(project.getDesignGaps().stream().anyMatch(gap ->
                "MISSING_DESIGN_ID".equals(gap.code())
                        && gap.message().contains("business.directories[].actions[]")));
    }

    private static void write(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }
}
