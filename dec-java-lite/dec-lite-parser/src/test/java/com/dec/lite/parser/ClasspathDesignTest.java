package com.dec.lite.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ClasspathDesignTest {
    @TempDir Path temp;
    private static final String XML = "<enum-config><enum name=\"A\"><enum-value name=\"YES\" value=\"1\"/></enum></enum-config>";

    @Test void loadsSameDesignFromExplodedDirectoryAndJar() throws Exception {
        Map<String, String> resources = Map.of("dec/design-files.txt", "design/enums.xml\n",
                "dec/design/enums.xml", XML);
        for (var entry : resources.entrySet()) {
            Path path = temp.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
        }
        Path jar = jar("design.jar", resources);
        try (var directory = loader(temp); var packaged = loader(jar)) {
            var parser = new DecXmlParser();
            var a = parser.parseClasspath(directory, "dec/design-files.txt");
            var b = parser.parseClasspath(packaged, "dec/design-files.txt");
            assertEquals(a.getDesignIds(), b.getDesignIds());
            assertEquals(a.getDocuments(), b.getDocuments());
            assertTrue(b.getDesignIds().contains("ENUM-A"));
        }
    }

    @Test void rejectsMissingEmptyDuplicateAndTraversalEntries() throws Exception {
        for (String manifest : new String[]{"", "design/missing.xml", "../outside.xml", "/absolute.xml",
                "design/enums.xml\ndesign/enums.xml", "design/../enums.xml"}) {
            try (var loader = loader(jar("bad.jar", Map.of("dec/design-files.txt", manifest,
                    "dec/design/enums.xml", XML)))) {
                assertThrows(DecParseException.class, () -> new DecXmlParser().parseClasspath(loader, "dec/design-files.txt"));
            }
        }
        try (var loader = loader(temp)) {
            assertThrows(DecParseException.class, () -> new DecXmlParser().parseClasspath(loader, "missing.txt"));
        }
    }

    @Test void rejectsAmbiguousClasspathResources() throws Exception {
        Path first = jar("first.jar", Map.of("dec/design-files.txt", "design/enums.xml", "dec/design/enums.xml", XML));
        Path second = jar("second.jar", Map.of("dec/design/enums.xml", XML));
        try (var loader = loader(first, second)) {
            assertThrows(DecParseException.class, () -> new DecXmlParser().parseClasspath(loader, "dec/design-files.txt"));
        }
        Path third = jar("third.jar", Map.of("dec/design-files.txt", "design/enums.xml"));
        try (var loader = loader(first, third)) {
            assertThrows(DecParseException.class, () -> new DecXmlParser().parseClasspath(loader, "dec/design-files.txt"));
        }
    }

    @Test void preservesXmlSecurityAndDocumentSizeLimitForStreams() throws Exception {
        for (String xml : new String[]{"<!DOCTYPE enum-config [<!ENTITY x SYSTEM 'file:///not-readable'>]>" + XML,
                " ".repeat(8_000_001), "<enum-config>"}) {
            try (var loader = loader(jar("unsafe.jar", Map.of("dec/design-files.txt", "design/enums.xml",
                    "dec/design/enums.xml", xml)))) {
                assertThrows(DecParseException.class, () -> new DecXmlParser().parseClasspath(loader, "dec/design-files.txt"));
            }
        }
    }

    private URLClassLoader loader(Path... paths) throws Exception {
        var urls = new java.net.URL[paths.length];
        for (int i = 0; i < paths.length; i++) urls[i] = paths[i].toUri().toURL();
        return new URLClassLoader(urls, null);
    }

    private Path jar(String name, Map<String, String> entries) throws Exception {
        Path file = temp.resolve(name);
        try (var jar = new JarOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return file;
    }
}
