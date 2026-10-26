package com.dec.lite.generator;

import com.dec.lite.model.DecProject;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.dec.lite.generator.JavaModelSupport.*;
import static com.dec.lite.generator.ApiModelBuilder.apiGroups;

/** Validates, assembles and emits a deterministic Java project. */
public final class GeneratorPipeline {
    private static final String DEFAULT_BASE_PACKAGE = "com.dec.generated";
    public GenerationResult generate(DecProject project, Path output) {
        return generate(project, output, DEFAULT_BASE_PACKAGE);
    }

    public GenerationResult generate(DecProject project, Path output, String basePackage) {
        return generate(project, output, basePackage, GenerationOptions.defaults());
    }

    public GenerationResult generate(DecProject project, Path output, String basePackage, GenerationOptions options) {
        var plan = render(project, output, basePackage, options, false);
        return new GenerationResult(plan.output(), plan.files());
    }

    /** Render and validate without creating, deleting or modifying anything in output. */
    public GenerationPlan preview(DecProject project, Path output, String basePackage, GenerationOptions options) {
        return render(project, output, basePackage, options, true);
    }

    private GenerationPlan render(DecProject project, Path output, String basePackage, GenerationOptions options, boolean dryRun) {
        java.util.Objects.requireNonNull(options, "options");
        if (project == null) throw new IllegalArgumentException("project is required");
        if (output == null) throw new IllegalArgumentException("output is required");
        if (basePackage == null || basePackage.isBlank()) throw new IllegalArgumentException("base package is required");
        if (!basePackage.matches("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
            throw new GenerationException("invalid Java base package: " + basePackage);
        if (!project.getDesignGaps().isEmpty()) {
            throw new GenerationException("cannot generate while DESIGN_GAP exists: "
                    + project.getDesignGaps().stream().map(Object::toString).collect(Collectors.joining("; ")));
        }

        Path target = output.toAbsolutePath().normalize();
        GenerationContext context = GenerationContextBuilder.buildContext(project, basePackage, options);
        GeneratedProjectWriter writer = new GeneratedProjectWriter();
        List<String> designResources = GeneratedProjectWriter.designResources(project);
        GeneratedProjectWriter.prepareOutput(target);
        Path destination = target;
        List<String> generated = new ArrayList<>();
        try (var staging = GeneratedProjectWriter.staging()) {
            target = staging.path();
            writer.writeTemplate(target, "pom.xml.ftl", context.projectModel(), "pom.xml", generated);
            writer.writeTemplate(target, "datasources.properties.ftl", Map.of("dataSources", context.dataSources()),
                    "src/main/resources/dec/datasources.properties", generated);
            writer.writeTemplate(target, "application.java.ftl", context.applicationModel(),
                    javaPath(context.basePackage(), "GeneratedApplication.java"), generated);
            writer.writeTemplate(target, "custom-action-configuration.java.ftl", Map.of("package", context.basePackage() + ".config"),
                    javaPath(context.basePackage() + ".config", "CustomActionConfiguration.java"), generated);
            writer.writeTemplate(target, "action-listener-configuration.java.ftl", Map.of("package", context.basePackage() + ".config"),
                    javaPath(context.basePackage() + ".config", "ActionListenerConfiguration.java"), generated);
            writer.writeTemplate(target, "datasource-adapter-configuration.java.ftl", Map.of("package", context.basePackage() + ".config"),
                    javaPath(context.basePackage() + ".config", "DataSourceAdapterConfiguration.java"), generated);
            for (DesignProjectionGenerator.Source source : context.projections()) {
                writer.writeTemplate(target, source.template(), source.model(), source.path(), generated);
            }
            for (Map<String, Object> model : context.entities()) {
                writer.writeTemplate(target, "entity.java.ftl", model,
                        javaPath(String.valueOf(model.get("package")), model.get("className") + ".java"), generated);
            }
            for (Map<String, Object> model : context.enums()) {
                writer.writeTemplate(target, "enum.java.ftl", model,
                        javaPath(String.valueOf(model.get("package")), model.get("className") + ".java"), generated);
            }
            for (Map<String, Object> model : context.views()) {
                writer.writeTemplate(target, "view.java.ftl", model,
                        javaPath(String.valueOf(model.get("package")), model.get("className") + ".java"), generated);
            }
            for (Map<String, Object> model : context.requests()) {
                writer.writeTemplate(target, "request.java.ftl", model,
                        javaPath(String.valueOf(model.get("package")), model.get("className") + ".java"), generated);
            }
            for (Map<String, Object> model : apiGroups(context.apis())) {
                writer.writeTemplate(target, "controller.java.ftl", model,
                        javaPath(String.valueOf(model.get("package")), model.get("className") + "Controller.java"), generated);
                writer.writeTemplate(target, "service.java.ftl", model,
                        javaPath(String.valueOf(model.get("servicePackage")), model.get("className") + "Service.java"), generated);
            }
            for (Map<String, Object> model : context.entities()) {
                writer.writeTemplate(target, "mapper.java.ftl", model,
                        javaPath(siblingPackage(String.valueOf(model.get("package")), "mapper"), model.get("className") + "Mapper.java"), generated);
                writer.writeTemplate(target, "dao.java.ftl", model,
                        javaPath(siblingPackage(String.valueOf(model.get("package")), "dao"), model.get("className") + "Dao.java"), generated);
            }
            DesignIndexWriter.writeDesignIndex(target, project, context, generated);
            GeneratedProjectWriter.writeDesignManifest(target, designResources, generated);
            GeneratedProjectWriter.writeOptions(target, options, generated);
            GeneratedProjectWriter.writeManifest(target, generated);
            var plan = GeneratedProjectWriter.plan(target, destination, generated);
            if (!dryRun) GeneratedProjectWriter.commit(target, destination, generated);
            return plan;
        } catch (IOException | TemplateException e) {
            throw new GenerationException("cannot generate Java project in " + destination, e);
        }
    }

    public record GenerationResult(Path output, List<String> files) { }
    public record GenerationPlan(Path output, List<String> files, List<String> created, List<String> updated,
                                 List<String> unchanged, List<String> deleted, List<String> preserved,
                                 List<String> conflicts) { }
}
