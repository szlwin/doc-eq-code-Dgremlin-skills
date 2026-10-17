package com.dec.lite.action;

import com.dec.lite.information.RuleViewKey;
import com.dec.lite.information.RuleViewRegistry;
import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles canonical RuleViews with ownership from System.ruleFiles. */
public final class RuleViewCompiler {
    public Map<RuleViewKey, CompiledRuleView> compile(DecProject project) {
        Map<String, String> ownerByRuleFile = owners(project);
        Map<RuleViewKey, CompiledRuleView> result = new LinkedHashMap<>();
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() != DecKind.RULE) continue;
            String fileStem = stem(document.source().getFileName().toString());
            String system = ownerByRuleFile.get(fileStem);
            for (Object raw : list(document.root().get("ruleViews"), "ruleViews")) {
                Map<String, Object> view = map(raw, "ruleView");
                String explicitSystem = optional(view, "system");
                String owner = explicitSystem != null ? explicitSystem : system;
                if (owner == null) throw new ActionExecutionException("RuleView owner System is unknown for " + document.source());
                if (explicitSystem != null && system != null && !explicitSystem.equals(system)) throw new ActionExecutionException("RuleView System conflicts with system.ruleFiles: " + view.get("name"));
                RuleViewKey key = new RuleViewKey(owner, text(view, "viewRef", "ruleView"), text(view, "name", "ruleView"));
                List<CompiledRule> rules = new ArrayList<>();
                for (Object item : list(view.get("rules"), "ruleView.rules")) {
                    Map<String, Object> rule = map(item, "rule");
                    String type = text(rule, "type", "rule");
                    if (type.equals("grammer")) type = "dsl";
                    validate(rule, type);
                    if (!List.of("check", "checkPattern", "checkData", "checkDataPattern", "insert", "update", "delete", "get", "query", "dsl").contains(type)) throw new ActionExecutionException("unsupported Rule type: " + type);
                    rules.add(new CompiledRule(text(rule, "id", "rule"), text(rule, "name", "rule"), type,
                            optional(rule, "property"), optional(rule, "pattern"), alias(rule, "process", "grammer"),
                            optional(rule, "dataSource") != null ? optional(rule, "dataSource") : optional(view, "dataSource"),
                            alias(rule, "cmd", "sql"), error(rule),
                            rule.get("customer") == null ? Map.of() : map(rule.get("customer"), "rule.customer")));
                }
                if (rules.stream().map(CompiledRule::name).distinct().count() != rules.size())
                    throw new ActionExecutionException("duplicate Rule name in " + key);
                if (rules.isEmpty()) throw new ActionExecutionException("RuleView requires at least one rule: " + key);
                if (result.putIfAbsent(key, new CompiledRuleView(key, text(view, "id", "ruleView"), rules)) != null) throw new ActionExecutionException("duplicate RuleView: " + key);
            }
        }
        return Map.copyOf(result);
    }

    public RuleViewRegistry register(DecProject project, DataOperationAdapter adapter) {
        var actions = new BusinessActionParser().parse(project);
        var recognitionRules = new com.dec.lite.information.InformationParser().parse(project).definitions().values().stream()
                .filter(d -> d.ruleRef() != null).map(d -> new RuleViewKey(d.key().system(), d.viewRef(), d.ruleRef()))
                .collect(java.util.stream.Collectors.toSet());
        return bind(compile(project), adapter, actions, businessDataSource(project), recognitionRules);
    }

    /** Bind immutable compiled rules to one request adapter; never cache the resulting registry globally. */
    public RuleViewRegistry bind(Map<RuleViewKey, CompiledRuleView> compiled, DataOperationAdapter adapter) {
        return bind(compiled, adapter, List.of(), null);
    }

    public RuleViewRegistry bind(Map<RuleViewKey, CompiledRuleView> compiled, DataOperationAdapter adapter,
                                 List<ActionDefinition> actions, String defaultDataSource) {
        return bind(compiled, adapter, actions, defaultDataSource, compiled.keySet());
    }
    public RuleViewRegistry bind(Map<RuleViewKey, CompiledRuleView> compiled, DataOperationAdapter adapter,
                                 List<ActionDefinition> actions, String defaultDataSource, java.util.Set<RuleViewKey> recognitionRules) {
        if (adapter instanceof DataSourceSelection selection) selection.validate(compiled.values(), actions, defaultDataSource, recognitionRules);
        RuleViewRegistry registry = new RuleViewRegistry(defaultDataSource);
        RuleViewInterpreter interpreter = new RuleViewInterpreter(adapter);
        for (CompiledRuleView view : Map.copyOf(compiled).values()) {
            RuleViewKey key = view.key();
            registry.register(key.system(), key.viewRef(), key.ruleRef(), invocation -> interpreter.execute(view, invocation));
        }
        return registry;
    }

    /** A DirectoryWorkflowRunner owns one Business; do not guess a default across flows. */
    public static String businessDataSource(DecProject project) {
        var businesses = project.getDocuments().stream().filter(d -> d.kind() == DecKind.BUSINESS).toList();
        if (businesses.size() > 1) throw new ActionExecutionException("Select one Business per workflow runner");
        return businesses.isEmpty() ? null : optional(map(businesses.get(0).root().get("business"), "business"), "dataSource");
    }

    private static void validate(Map<String, Object> rule, String type) {
        String name = text(rule, "name", "rule");
        if (List.of("check", "checkData", "checkDataPattern", "insert", "update", "get", "query").contains(type))
            text(rule, "property", "Rule " + name);
        if (List.of("check", "checkData", "checkPattern", "checkDataPattern").contains(type))
            text(rule, "pattern", "Rule " + name);
        if (type.equals("delete") && optional(rule, "property") == null && alias(rule, "cmd", "sql") == null)
            throw new ActionExecutionException("delete requires property or cmd: " + name);
        if (type.equals("dsl") && alias(rule, "process", "grammer") == null)
            throw new ActionExecutionException("dsl requires process: " + name);
        if (type.equals("check") || type.equals("checkData")) RuleChecks.validate(text(rule, "pattern", name));
        if (type.equals("checkPattern") || type.equals("checkDataPattern")) RuleExpression.compile(text(rule, "pattern", name));
        if (type.equals("dsl")) RuleDsl.compile(alias(rule, "process", "grammer"));
    }
    private static String alias(Map<String, Object> rule, String canonical, String legacy) {
        String value = optional(rule, canonical), old = optional(rule, legacy);
        if (value != null && old != null && !value.equals(old))
            throw new ActionExecutionException("conflicting Rule fields: " + canonical + "/" + legacy);
        return value != null ? value : old;
    }
    private static CompiledRule.RuleError error(Map<String, Object> rule) {
        if (rule.get("error") == null) return null;
        Map<String, Object> error = map(rule.get("error"), "rule.error");
        return new CompiledRule.RuleError(text(error, "code", "rule.error"), text(error, "message", "rule.error"),
                error.get("level") == null ? null : String.valueOf(error.get("level")));
    }

    private static Map<String, String> owners(DecProject project) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() != DecKind.SYSTEMS) continue;
            for (Object raw : list(document.root().get("systems"), "systems")) {
                Map<String, Object> system = map(raw, "system");
                String name = text(system, "name", "system");
                for (Object file : listOrEmpty(system.get("ruleFiles"))) {
                    if (!(file instanceof String path)) throw new ActionExecutionException("system.ruleFiles entries must be strings");
                    String stem = stem(path.substring(path.lastIndexOf('/') + 1));
                    String previous = owners.putIfAbsent(stem, name);
                    if (previous != null && !previous.equals(name)) throw new ActionExecutionException("rule file belongs to multiple Systems: " + stem);
                }
            }
        }
        return owners;
    }
    private static String stem(String value) { int dot = value.lastIndexOf('.'); return dot < 0 ? value : value.substring(0, dot); }
    private static String text(Map<String, Object> map, String key, String context) { return com.dec.lite.model.DecFields.requiredText(map, key, context, ActionExecutionException::new); }
    private static String optional(Map<String, Object> map, String key) { return map.get(key) instanceof String text && !text.isBlank() ? text : null; }
    private static List<?> list(Object value, String context) { return com.dec.lite.model.DecFields.list(value, context, ActionExecutionException::new); }
    private static List<?> listOrEmpty(Object value) { return value instanceof List<?> list ? list : List.of(); }
    private static Map<String, Object> map(Object value, String context) {
        return com.dec.lite.model.DecFields.map(value, context, ActionExecutionException::new);
    }
}
