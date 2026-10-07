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
                    if (!List.of("check", "checkPattern", "checkData", "checkDataPattern", "insert", "update", "delete", "get", "query", "dsl").contains(type)) throw new ActionExecutionException("unsupported Rule type: " + type);
                    rules.add(new CompiledRule(text(rule, "id", "rule"), text(rule, "name", "rule"), type,
                            optional(rule, "property"), optional(rule, "pattern"), optional(rule, "process"),
                            optional(rule, "dataSource") != null ? optional(rule, "dataSource") : optional(view, "dataSource")));
                }
                if (rules.isEmpty()) throw new ActionExecutionException("RuleView requires at least one rule: " + key);
                if (result.putIfAbsent(key, new CompiledRuleView(key, text(view, "id", "ruleView"), rules)) != null) throw new ActionExecutionException("duplicate RuleView: " + key);
            }
        }
        return Map.copyOf(result);
    }

    public RuleViewRegistry register(DecProject project, DataOperationAdapter adapter) {
        RuleViewRegistry registry = new RuleViewRegistry();
        RuleViewInterpreter interpreter = new RuleViewInterpreter(adapter);
        for (CompiledRuleView view : compile(project).values()) {
            RuleViewKey key = view.key();
            registry.register(key.system(), key.viewRef(), key.ruleRef(), invocation -> interpreter.execute(view, invocation));
        }
        return registry;
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
    private static String text(Map<String, Object> map, String key, String context) { String result = optional(map, key); if (result == null) throw new ActionExecutionException(context + " requires " + key); return result; }
    private static String optional(Map<String, Object> map, String key) { return map.get(key) instanceof String text && !text.isBlank() ? text : null; }
    private static List<?> list(Object value, String context) { if (!(value instanceof List<?> list)) throw new ActionExecutionException(context + " must be a list"); return list; }
    private static List<?> listOrEmpty(Object value) { return value instanceof List<?> list ? list : List.of(); }
    private static Map<String, Object> map(Object value, String context) {
        if (!(value instanceof Map<?, ?> raw)) throw new ActionExecutionException(context + " must be a mapping");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new ActionExecutionException(context + " keys must be strings");
            result.put(key, entry.getValue());
        }
        return result;
    }
}
