package com.dec.lite.query;

import com.dec.lite.action.*;
import com.dec.lite.directory.DirectoryGraph;
import com.dec.lite.directory.DirectoryGraphCompiler;
import com.dec.lite.information.*;
import com.dec.lite.model.DecProject;
import com.dec.lite.model.SemanticDigest;
import com.dec.lite.parser.DecXmlParser;
import com.dec.lite.parser.DecYamlParser;
import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.runtime.RuntimeFailure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Isolated load, compile and readiness validation followed by one atomic publish. */
public final class RuntimeCatalog {
    private final AtomicReference<RuntimeSnapshot> current = new AtomicReference<>();
    private final Function<DecProject, RuleViewRegistry> ruleViewFactory;
    private final Function<DecProject, CustomActionRegistry> customActionFactory;

    public RuntimeCatalog(Path initial, Function<DecProject, RuleViewRegistry> ruleViewFactory,
                          Function<DecProject, CustomActionRegistry> customActionFactory) {
        this.ruleViewFactory = ruleViewFactory; this.customActionFactory = customActionFactory;
        reload(initial);
    }
    public RuntimeSnapshot current() { return current.get(); }

    public RuntimeSnapshot reload(Path input) {
        RuntimeSnapshot candidate = compile(input);
        current.set(candidate);
        return candidate;
    }
    private RuntimeSnapshot compile(Path input) {
        DecProject project = load(input);
        if (!project.getDesignGaps().isEmpty())
            throw new RuntimeFailure(RuntimeErrorCode.COMPILATION, null, input, "unresolved DESIGN_GAP: " + project.getDesignGaps(), null);
        InformationCompilation information = new InformationParser().parse(project);
        CustomActionRegistry customs = customActionFactory.apply(project);
        if (customs == null) throw new IllegalArgumentException("Custom Action factory returned null");
        DirectoryGraph graph = new DirectoryGraphCompiler().compileReady(project, information, customs);
        RuleViewRegistry registry = ruleViewFactory.apply(project);
        if (registry == null) throw new IllegalArgumentException("RuleView factory returned null");
        for (InformationDefinition definition : information.definitions().values()) {
            if (definition.ruleRef() != null && !registry.contains(definition.key().system(), definition.viewRef(), definition.ruleRef()))
                throw new RuntimeFailure(RuntimeErrorCode.COMPILATION, definition.designId(), definition.source(),
                        "RuleView evaluator is not registered: " + definition.key(), null);
        }
        var views = new RuleViewCompiler().compile(project);
        for (ActionDefinition action : new BusinessActionParser().parse(project)) if (action.isRuleView()) {
            var key = views.keySet().stream().filter(k -> k.system().equals(action.systemRef()) && k.ruleRef().equals(action.ruleRef())).findFirst().orElseThrow();
            if (!registry.contains(key.system(), key.viewRef(), key.ruleRef()))
                throw new RuntimeFailure(RuntimeErrorCode.COMPILATION, action.id(), action.source(),
                        "Action RuleView evaluator is not registered: " + key, null);
        }
        QueryCompiler queries = new QueryCompiler(project, graph, information);
        return new RuntimeSnapshot(project, information, graph, registry.freeze(), customs.freeze(), queries,
                SystemAccessPolicy.compile(project), SemanticDigest.project(project));
    }
    private static DecProject load(Path input) {
        if (input == null) throw new IllegalArgumentException("DEC input path is required");
        if (Files.isRegularFile(input))
            return input.toString().endsWith(".xml") ? new DecXmlParser().parse(input) : new DecYamlParser().parse(input);
        try (var files = Files.walk(input)) {
            List<String> extensions = files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".xml") || name.endsWith(".yaml") || name.endsWith(".yml"))
                    .map(name -> name.substring(name.lastIndexOf('.') + 1)).distinct().toList();
            if (extensions.contains("xml") && extensions.size() > 1)
                throw new IllegalArgumentException("mixed XML/YAML input is ambiguous; choose one frontend directory");
            return extensions.contains("xml") ? new DecXmlParser().parse(input) : new DecYamlParser().parse(input);
        } catch (java.io.IOException failure) { throw new IllegalArgumentException("cannot list DEC input", failure); }
    }
}
