package com.dec.lite.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;

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
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            return new Result(Main.run(args), output.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(previous);
        }
    }

    private record Result(int code, String error) { }
}
