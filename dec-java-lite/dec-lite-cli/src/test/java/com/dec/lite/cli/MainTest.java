package com.dec.lite.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
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
