package com.dec.lite.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedProjectWriterTest {
    @TempDir Path temp;

    @Test void nestedManifestPathsAllowRegenerationOnTheHostFilesystem() throws Exception {
        Path target = temp.resolve("output");
        String relative = "src/main/java/example/App.java";
        for (String content : List.of("first generation", "second generation")) {
            try (var stage = GeneratedProjectWriter.staging()) {
                Path source = stage.path().resolve(relative);
                Files.createDirectories(source.getParent());
                Files.writeString(source, content);
                GeneratedProjectWriter.writeManifest(stage.path(), List.of(relative));
                GeneratedProjectWriter.commit(stage.path(), target, List.of(relative));
            }
            assertEquals(content, Files.readString(target.resolve(relative)));
            assertEquals(List.of(relative), Files.readAllLines(target.resolve("dec-generated-manifest.txt")));
            if (content.equals("first generation"))
                Files.writeString(target.resolve("handwritten.txt"), "keep");
        }
        assertEquals("keep", Files.readString(target.resolve("handwritten.txt")));
    }

    @Test void portableComparisonStillRejectsNonCanonicalManifestEntries() throws Exception {
        Path target = Files.createDirectory(temp.resolve("invalid-manifest"));
        Path source = target.resolve("src/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "keep");
        for (String relative : List.of("src//App.java", "src/./App.java", "src\\App.java", "../App.java")) {
            GeneratedProjectWriter.writeManifest(target, List.of(relative));
            assertThrows(GenerationException.class, () -> GeneratedProjectWriter.prepareOutput(target), relative);
            assertEquals("keep", Files.readString(source));
        }
    }

    @Test void ioFailureAfterAWriteRestoresFilesManifestAndRemovesNewDirectories() throws Exception {
        Path target = Files.createDirectory(temp.resolve("output"));
        Files.writeString(target.resolve("existing.txt"), "previous");
        GeneratedProjectWriter.writeManifest(target, List.of("existing.txt"));
        String manifest = Files.readString(target.resolve("dec-generated-manifest.txt"));
        try (var stage = GeneratedProjectWriter.staging()) {
            Files.writeString(stage.path().resolve("existing.txt"), "replacement");
            Files.createDirectories(stage.path().resolve("nested"));
            Files.writeString(stage.path().resolve("nested/new.txt"), "new");
            var files = List.of("existing.txt", "nested/new.txt", "missing.txt");
            GeneratedProjectWriter.writeManifest(stage.path(), files);
            assertThrows(java.io.IOException.class, () -> GeneratedProjectWriter.commit(stage.path(), target, files));
        }
        assertEquals("previous", Files.readString(target.resolve("existing.txt")));
        assertEquals(manifest, Files.readString(target.resolve("dec-generated-manifest.txt")));
        assertFalse(Files.exists(target.resolve("nested")));
        assertFalse(Files.exists(target.resolve("missing.txt")));
    }

    @Test void lateTemplateFailureCannotTouchPreviousOutput() throws Exception {
        Path target = Files.createDirectory(temp.resolve("output"));
        Files.writeString(target.resolve("existing.txt"), "previous");
        GeneratedProjectWriter.writeManifest(target, List.of("existing.txt"));
        GeneratedProjectWriter.prepareOutput(target);
        try (var stage = GeneratedProjectWriter.staging()) {
            Files.writeString(stage.path().resolve("existing.txt"), "replacement");
            assertThrows(java.io.IOException.class, () -> new GeneratedProjectWriter().writeTemplate(
                    stage.path(), "absent-template.ftl", Map.of(), "other.txt", new ArrayList<>()));
        }
        assertEquals("previous", Files.readString(target.resolve("existing.txt")));
        assertEquals(List.of("existing.txt"), Files.readAllLines(target.resolve("dec-generated-manifest.txt")));
    }
}
