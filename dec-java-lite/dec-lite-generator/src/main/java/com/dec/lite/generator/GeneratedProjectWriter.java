package com.dec.lite.generator;

import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecProject;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Renders templates and owns safe output cleanup and the generated file manifest. */
final class GeneratedProjectWriter {
    static final String MARKER = ".dec-generated";
    private final Configuration freemarker;

    GeneratedProjectWriter() {
        freemarker = new Configuration(Configuration.VERSION_2_3_34);
        freemarker.setDefaultEncoding(StandardCharsets.UTF_8.name());
        freemarker.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        freemarker.setLogTemplateExceptions(false);
        freemarker.setFallbackOnNullLoopVariable(false);
    }
    void writeTemplate(Path target, String templateName, Map<String, Object> model,
                               String relative, List<String> generated) throws IOException, TemplateException {
        Template template = freemarker.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        Path file = safePath(target, relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, writer.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        generated.add(relative.replace('\\', '/'));
    }

    static void writeManifest(Path target, List<String> generated) throws IOException {
        List<String> sorted = generated.stream().distinct().sorted().toList();
        Files.writeString(target.resolve(MARKER), "dec-java-lite generated output\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.writeString(target.resolve("dec-generated-manifest.txt"), String.join("\n", sorted) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    static void prepareOutput(Path target) {
        try {
            Path systemTemp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
            for (Path ancestor = target; ancestor != null; ancestor = ancestor.getParent()) {
                if (Files.isSymbolicLink(ancestor) && !(target.startsWith(systemTemp) && systemTemp.startsWith(ancestor)))
                    throw new GenerationException("output path contains a symbolic link: " + ancestor);
            }
            if (Files.exists(target) && !Files.isDirectory(target)) throw new GenerationException("output is not a directory: " + target);
            Files.createDirectories(target);
            try (var paths = Files.walk(target)) {
                if (paths.anyMatch(Files::isSymbolicLink)) throw new GenerationException("generated output contains a symbolic link: " + target);
            }
            try (var stream = Files.list(target)) {
                if (stream.findAny().isPresent() && !Files.exists(target.resolve(MARKER))) {
                    throw new GenerationException("refusing to overwrite non-generated directory: " + target);
                }
            }
            Path manifest = target.resolve("dec-generated-manifest.txt");
            if (Files.exists(target.resolve(MARKER)) && Files.exists(manifest)) {
                List<Path> staleFiles = new ArrayList<>();
                for (String relative : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                    if (relative.isBlank() || relative.contains("..") || relative.startsWith("/"))
                        throw new GenerationException("unsafe generated manifest entry: " + relative);
                    staleFiles.add(safePath(target, relative));
                }
                for (Path staleFile : staleFiles) Files.deleteIfExists(staleFile);
            }
        } catch (IOException e) {
            throw new GenerationException("cannot prepare output directory " + target, e);
        }
    }
    private static Path safePath(Path target, String relative) {
        Path path = Path.of(relative);
        Path resolved = target.resolve(path).normalize();
        if (path.isAbsolute() || !resolved.startsWith(target))
            throw new GenerationException("generated path escapes output directory: " + relative);
        return resolved;
    }

    /** Derived list only: package the corresponding canonical XML export under dec/design. */
    static List<String> designResources(DecProject project) {
        Set<String> names = new java.util.TreeSet<>();
        for (DecDocument document : project.getDocuments()) {
            String name = document.source().getFileName().toString().replaceFirst("\\.ya?ml$", ".xml");
            if (!name.matches("[A-Za-z0-9_$.-]+\\.xml") || !names.add("design/" + name))
                throw new GenerationException("invalid or duplicate packaged design filename: " + name);
        }
        return List.copyOf(names);
    }

    static void writeDesignManifest(Path target, List<String> names, List<String> generated) throws IOException {
        String relative = "src/main/resources/dec/design-files.txt";
        Path file = safePath(target, relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", names) + "\n", StandardCharsets.UTF_8);
        generated.add(relative);
    }
    static void writeOptions(Path target, GenerationOptions options, List<String> generated) throws IOException {
        if (options.apiGroups().isEmpty()) return; // preserve byte-identical default output
        String relative = "dec-generation-options.properties";
        Files.writeString(safePath(target, relative), options.propertiesText(), StandardCharsets.UTF_8);
        generated.add(relative);
    }
}
