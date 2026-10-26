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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Comparator;

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
            if (!Files.exists(target)) return;
            try (var paths = Files.walk(target)) {
                if (paths.anyMatch(Files::isSymbolicLink)) throw new GenerationException("generated output contains a symbolic link: " + target);
            }
            try (var stream = Files.list(target)) {
                if (stream.findAny().isPresent() && !Files.exists(target.resolve(MARKER))) {
                    throw new GenerationException("refusing to overwrite non-generated directory: " + target);
                }
            }
            if (Files.exists(target.resolve(MARKER))) ownedFiles(target);
        } catch (IOException e) {
            throw new GenerationException("cannot prepare output directory " + target, e);
        }
    }
    private static final String MANIFEST = "dec-generated-manifest.txt";

    /** Render in isolation: even a late template failure must leave the old project untouched. */
    static Staging staging() throws IOException { return new Staging(Files.createTempDirectory("dec-generation-")); }
    record Staging(Path path) implements AutoCloseable {
        @Override public void close() throws IOException {
            try (var paths = Files.walk(path)) {
                for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static Set<String> ownedFiles(Path target) throws IOException {
        Path manifest = target.resolve(MANIFEST);
        if (!Files.isRegularFile(target.resolve(MARKER)) || !Files.isRegularFile(manifest))
            throw new GenerationException("generated output requires a regular marker and manifest: " + target);
        Set<String> owned = new LinkedHashSet<>();
        for (String relative : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (relative.isBlank() || relative.contains("..") || relative.contains("\\")
                    || relative.startsWith("/") || relative.equals(MARKER) || relative.equals(MANIFEST))
                throw new GenerationException("unsafe generated manifest entry: " + relative);
            Path file = safePath(target, relative);
            // Manifest paths always use '/', including when the host Path uses '\\'.
            if (!target.relativize(file).toString().replace('\\', '/').equals(relative))
                throw new GenerationException("non-canonical generated manifest entry: " + relative);
            owned.add(relative);
        }
        return owned;
    }

    /** The preview and commit share exactly the same ownership checks. */
    static GeneratorPipeline.GenerationPlan plan(Path staged, Path target, List<String> generated) throws IOException {
        prepareOutput(target);
        Set<String> owned = Files.exists(target.resolve(MARKER)) ? ownedFiles(target) : Set.of();
        Set<String> next = new LinkedHashSet<>(generated);
        List<String> conflicts = new ArrayList<>();
        for (String relative : next) {
            Path file = safePath(target, relative);
            if (Files.exists(file) && (!owned.contains(relative) || !Files.isRegularFile(file))) conflicts.add(relative);
            for (Path parent = file.getParent(); parent != null && parent.startsWith(target); parent = parent.getParent()) {
                if (Files.exists(parent) && !Files.isDirectory(parent)) {
                    conflicts.add(target.relativize(parent).toString()); break;
                }
            }
        }
        for (String relative : owned) {
            Path file = safePath(target, relative);
            if (Files.exists(file) && !Files.isRegularFile(file)) conflicts.add(relative);
        }
        List<String> created = new ArrayList<>(), updated = new ArrayList<>(), unchanged = new ArrayList<>();
        for (String relative : next) {
            Path file = safePath(target, relative);
            if (!Files.exists(file)) created.add(relative);
            else if (Files.isRegularFile(file) && Files.mismatch(staged.resolve(relative), file) == -1) unchanged.add(relative);
            else updated.add(relative);
        }
        List<String> preserved = new ArrayList<>();
        if (Files.exists(target)) try (var paths = Files.walk(target)) {
            paths.filter(Files::isRegularFile).map(target::relativize)
                    .map(p -> p.toString().replace('\\', '/'))
                    .filter(p -> !owned.contains(p) && !next.contains(p) && !p.equals(MARKER) && !p.equals(MANIFEST))
                    .forEach(preserved::add);
        }
        return new GeneratorPipeline.GenerationPlan(target, List.copyOf(generated),
                created.stream().sorted().toList(), updated.stream().sorted().toList(), unchanged.stream().sorted().toList(),
                owned.stream().filter(p -> !next.contains(p)).sorted().toList(), preserved.stream().sorted().toList(),
                conflicts.stream().distinct().sorted().toList());
    }

    /** Validate the complete write set before touching output; restore files on a failed commit. */
    static void commit(Path staged, Path target, List<String> generated) throws IOException {
        var plan = plan(staged, target, generated);
        if (!plan.conflicts().isEmpty()) throw new GenerationException("refusing conflicting generated output paths: "
                + String.join(", ", plan.conflicts()));
        Set<String> owned = Files.exists(target.resolve(MARKER)) ? ownedFiles(target) : Set.of();
        Set<String> next = new LinkedHashSet<>(generated);

        Set<String> affected = new LinkedHashSet<>(owned);
        affected.addAll(next); affected.add(MARKER); affected.add(MANIFEST);
        Map<Path, byte[]> previous = new LinkedHashMap<>();
        Set<Path> newDirectories = new LinkedHashSet<>();
        for (String relative : affected) {
            Path file = safePath(target, relative);
            previous.put(file, Files.exists(file) ? Files.readAllBytes(file) : null);
            for (Path parent = file.getParent(); parent != null && !Files.exists(parent); parent = parent.getParent())
                newDirectories.add(parent);
        }
        try {
            // Metadata is installed last, so it describes only a completed generation.
            for (String relative : next) install(staged.resolve(relative), safePath(target, relative));
            for (String relative : owned) if (!next.contains(relative)) Files.deleteIfExists(safePath(target, relative));
            install(staged.resolve(MARKER), target.resolve(MARKER));
            install(staged.resolve(MANIFEST), target.resolve(MANIFEST));
        } catch (IOException | RuntimeException failure) {
            for (var entry : previous.entrySet()) {
                try {
                    if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                    else Files.write(entry.getKey(), entry.getValue());
                } catch (IOException restoreFailure) { failure.addSuppressed(restoreFailure); }
            }
            for (Path directory : newDirectories.stream().sorted(Comparator.reverseOrder()).toList()) {
                try { Files.deleteIfExists(directory); }
                catch (IOException restoreFailure) { failure.addSuppressed(restoreFailure); }
            }
            throw failure;
        }
    }
    private static void install(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
