package com.dec.lite.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;

    @Test void dryRunDoesNotCreateOutputAndReportsConflictsWithNonzeroExit() throws Exception {
        var output = temp.resolve("preview");
        var first = run("generate", "--yaml", "../../demo/order-payment-yaml", "--output", output.toString(), "--dry-run");
        assertEquals(0, first.code(), first.error());
        assertTrue(first.output().contains("CREATE pom.xml"));
        assertTrue(!java.nio.file.Files.exists(output));
        assertEquals(0, run("generate", "--yaml", "../../demo/order-payment-yaml", "--output", output.toString()).code());
        var collision = output.resolve("src/main/java/com/dec/generated/order/service/OrderPaymentsService.java");
        java.nio.file.Files.writeString(collision, "handwritten");
        var preview = run("generate", "--yaml", "../../demo/order-payment-yaml", "--output", output.toString(),
                "--generation-config", "../../demo/order-payment-generation.properties", "--dry-run");
        assertEquals(1, preview.code(), preview.error());
        assertTrue(preview.output().contains("CONFLICT src/main/java/com/dec/generated/order/service/OrderPaymentsService.java"));
        assertEquals("handwritten", java.nio.file.Files.readString(collision));
    }

    @Test void explainsBusinessRangesAndRejectsMixedSelectorModes() {
        Result result = run("query", "--yaml", "../fixtures/mix", "--view", "OrderInfo", "--start", "ordered", "--end", "success", "--with", "user");
        assertEquals(0, result.code(), result.error());
        assertTrue(result.output().contains("selectedDirectories=[ordered, paying, success]"));
        assertTrue(result.output().contains("LEFT JOIN `user_info`"));
        assertEquals(2, run("query", "--yaml", "../fixtures/mix", "--view", "OrderInfo", "--directory", "ordered").code());
        assertEquals(2, run("query", "--yaml", "../fixtures/mix", "--view", "OrderInfo", "--from", "ordered").code());
        assertEquals(2, run("query", "--yaml", "../fixtures/mix", "--directory", "ordered", "--start", "ordered").code());
        assertEquals(2, run("query", "--yaml", "../fixtures/mix", "--view", "OrderInfo", "--eq", "success", "--end", "success").code());
    }

    @Test void generatesConfiguredGroupsThroughCli() throws Exception {
        var output = temp.resolve("out");
        Result result = run("generate", "--yaml", "../../demo/order-payment-yaml", "--output", output.toString(),
                "--base-package", "example.cli", "--generation-config", "../../demo/order-payment-generation.properties");
        assertEquals(0, result.code(), result.error());
        assertTrue(java.nio.file.Files.exists(output.resolve("src/main/java/example/cli/order/controller/OrderPaymentsController.java")));
        assertTrue(java.nio.file.Files.exists(output.resolve("src/main/java/example/cli/order/controller/OrdersController.java")));
    }

    @Test void missingOrInvalidGenerationConfigFailsWithoutCreatingOutput() throws Exception {
        var output = temp.resolve("out");
        Result missing = run("generate", "--yaml", "../fixtures/mix", "--output", output.toString(), "--generation-config");
        assertEquals(2, missing.code());
        assertTrue(missing.error().contains("--generation-config requires a value"));
        var config = temp.resolve("bad.properties");
        java.nio.file.Files.writeString(config, "version=dec-generation/v1\napi.group.API-TYPO=Other\n");
        Result invalid = run("generate", "--yaml", "../fixtures/mix", "--output", output.toString(), "--generation-config", config.toString());
        assertEquals(1, invalid.code());
        assertTrue(invalid.error().contains("unknown API Design ID"));
        assertTrue(!java.nio.file.Files.exists(output));
    }

    @Test void reportsMissingYamlValueAsUsageError() {
        Result result = run("inspect", "--yaml");
        assertEquals(2, result.code());
        assertTrue(result.error().contains("--yaml requires a value"));
    }

    @Test void reportsMissingTrailingQueryOptionValue() {
        Result result = run("query", "--yaml", "../fixtures/mix", "--directory");
        assertEquals(2, result.code());
        assertTrue(result.error().contains("--directory requires a value"));
    }

    @Test void reportsInvalidPageNumberWithoutStackTrace() {
        Result result = run("query", "--yaml", "../fixtures/mix", "--directory", "ordered", "--offset", "oops");
        assertEquals(2, result.code());
        assertTrue(result.error().contains("--offset requires an integer"));
        assertTrue(!result.error().contains("\tat "));
    }

    private static Result run(String... args) {
        PrintStream previous = System.err;
        PrintStream previousOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            System.setOut(out);
            return new Result(Main.run(args), output.toString(StandardCharsets.UTF_8), stdout.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(previous);
            System.setOut(previousOut);
        }
    }

    private record Result(int code, String error, String output) { }
}
