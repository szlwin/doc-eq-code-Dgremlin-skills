package com.dec.lite.action;

import com.dec.lite.information.InformationKey;
import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles canonical business Directory actions without creating a second runtime model. */
public final class BusinessActionParser {
    public List<ActionDefinition> parse(DecProject project) {
        List<ActionDefinition> result = new ArrayList<>();
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() != DecKind.BUSINESS) continue;
            Map<String, Object> business = map(document.root().get("business"), "business");
            for (Object raw : list(business.get("directories"), "business.directories")) {
                parseDirectory(map(raw, "directory"), document, result);
            }
        }
        return List.copyOf(result);
    }

    private void parseDirectory(Map<String, Object> directory, DecDocument document, List<ActionDefinition> result) {
        String owner = text(directory, "name", "directory");
        List<InformationKey> dependencies = informationList(directory.get("dependencies"), "directory.dependencies");
        InformationKey target = optionalInformation(directory.get("informationRef"), "directory.informationRef");
        InformationKey change = null;
        if (directory.get("change") instanceof Map<?, ?> rawChange) change = optionalInformation(map(rawChange, "directory.change").get("informationRef"), "directory.change.informationRef");
        for (Object raw : listOrEmpty(directory.get("actions"))) result.add(action(map(raw, "action"), owner, dependencies, target, change, document));
        for (Object raw : listOrEmpty(directory.get("subDirectories"))) {
            Map<String, Object> child = map(raw, "subDirectory");
            Object back = child.get("back");
            if (back instanceof Map<?, ?> backMap) {
                String rel = text(child, "rel", "subDirectory");
                for (Object action : listOrEmpty(map(backMap, "back").get("actions"))) result.add(action(map(action, "back.action"), owner + ".back." + rel, dependencies, target, change, document));
            }
        }
    }

    private static ActionDefinition action(Map<String, Object> value, String owner, List<InformationKey> dependencies,
                                           InformationKey target, InformationKey change, DecDocument document) {
        String id = text(value, "id", "action");
        String name = text(value, "name", "action");
        if (value.containsKey("ref-rule") || value.containsKey("refRule") || value.containsKey("rule-ref"))
            throw new ActionExecutionException("legacy Action rule reference is unsupported; use ruleRef: " + name);
        String rule = optional(value, "ruleRef");
        String custom = rule == null ? optional(value, "customType") : null;
        if (rule == null && custom == null) custom = name;
        String system = optional(value, "systemRef");
        if (rule != null && system == null) throw new ActionExecutionException("RuleView Action requires systemRef: " + name);
        List<ProduceDefinition> produces = new ArrayList<>();
        for (Object raw : listOrEmpty(value.get("produces"))) produces.add(produce(map(raw, "produce"), document));
        Map<String, Object> payload = new LinkedHashMap<>();
        Object payloadValue = value.get("payload");
        if (payloadValue instanceof Map<?, ?>) payload.putAll(map(payloadValue, "action.payload"));
        if (value.containsKey("viewRef")) payload.put("viewRef", value.get("viewRef"));
        return new ActionDefinition(id, name, owner, system, rule, custom, dependencies, target, change, produces, payload,
                optional(value, "failurePolicy"), document.source());
    }

    private static ProduceDefinition produce(Map<String, Object> value, DecDocument document) {
        String id = text(value, "id", "produce");
        String ref = text(value, "ref", "produce");
        InformationKey info = optionalInformation(value.get("informationRef"), "produce.informationRef");
        boolean required = !(value.get("required") instanceof Boolean b) || b;
        int multiplicity = value.get("multiplicity") instanceof Number n ? n.intValue() : 1;
        return new ProduceDefinition(id, ref, optional(value, "type"), info, required, multiplicity, optional(value, "scope"), document.source().toString());
    }

    private static InformationKey optionalInformation(Object value, String context) {
        if (value == null) return null;
        if (!(value instanceof String text) || !text.contains(".")) throw new ActionExecutionException(context + " must be system.name");
        return InformationKey.parse(text);
    }
    private static List<InformationKey> informationList(Object value, String context) {
        List<InformationKey> result = new ArrayList<>();
        for (Object raw : listOrEmpty(value)) result.add(optionalInformation(raw instanceof Map<?, ?> map ? map(map, context).get("informationRef") : raw, context));
        return result;
    }
    private static String text(Map<String, Object> value, String key, String context) {
        String result = optional(value, key); if (result == null) throw new ActionExecutionException(context + " requires " + key); return result;
    }
    private static String optional(Map<String, Object> value, String... keys) {
        for (String key : keys) if (value.get(key) instanceof String text && !text.isBlank()) return text;
        return null;
    }
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
