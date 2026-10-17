package com.dec.lite.action;

import com.dec.lite.information.*;
import java.util.*;

/** Generic Rule-to-driver bridge; no Rule IDs or business predicates belong here. */
public final class RuleDataAdapter implements DataOperationAdapter {
    private final RuleDataAccess access;
    public RuleDataAdapter(RuleDataAccess access) { this.access = Objects.requireNonNull(access, "DataSource driver"); }
    private RuleDataAccess.Result call(CompiledRule rule, String operation, RuleViewRegistry.Invocation invocation) {
        return Objects.requireNonNull(access.execute(new RuleDataAccess.Request(rule, operation, invocation.key(),
                invocation.context().values(), invocation.payload(), invocation.transaction())), "DataSource result");
    }
    @Override public List<Map<String,Object>> read(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        String operation = switch (rule.type()) {
            case "checkData", "get" -> "get";
            case "checkDataPattern", "query" -> "query";
            default -> throw new ActionExecutionException("Not a read Rule: " + rule.type());
        };
        return call(rule, operation, invocation).rows();
    }
    @Override public RecognitionResult execute(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        if (!Set.of("get", "query", "insert", "update", "delete").contains(rule.type()))
            throw new ActionExecutionException("Unsupported data operation: " + rule.type());
        if (invocation.information() != null && !Set.of("get", "query").contains(rule.type()))
            throw new ActionExecutionException("Information cannot execute " + rule.type());
        RuleDataAccess.Result result = call(rule, rule.type(), invocation);
        List<Map<String,Object>> rows = result.rows();
        if (rule.type().equals("get") && rows.size() > 1)
            throw new ActionExecutionException("get returned more than one row: " + rule.id());
        if (invocation.information() != null) {
            // Recognition may observe existence but never persist a read into the business model.
            return RecognitionResult.of(rows.isEmpty() ? RecognitionResult.Status.FALSE : RecognitionResult.Status.TRUE,
                    invocation.context().version(), rule.id());
        }
        if (rule.type().equals("get")) {
            if (rows.isEmpty()) return RecognitionResult.of(RecognitionResult.Status.FALSE, invocation.context().version(), rule.id());
            writeOne(rule, invocation, rows.get(0));
        } else if (rule.type().equals("query")) {
            if (rule.property().equals(invocation.key().viewRef())) throw new ActionExecutionException("query requires a collection property");
            invocation.write(rule.property(), new ModelContext(Map.of("rows", rows)).read("rows"));
        } else if (rule.type().equals("insert") && !rows.isEmpty()) {
            Object target = target(rule, invocation);
            if (target instanceof List<?> list) {
                if (list.size() != rows.size()) throw new ActionExecutionException("insert generated rows do not match collection size");
                for (int i = 0; i < list.size(); i++) {
                    Map<String,Object> merged = merge(list.get(i), rows.get(i));
                    invocation.write(rule.property() + "[" + i + "]", merged);
                }
            } else {
                if (rows.size() != 1) throw new ActionExecutionException("insert expected one generated row");
                writeOne(rule, invocation, rows.get(0));
            }
        }
        if (rule.property() != null && !rule.type().equals("delete")) {
            Object value = target(rule, invocation);
            if (value != null) invocation.produce(rule.property(), value);
        }
        return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), rule.id());
    }
    private static Object target(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        return rule.property().equals(invocation.key().viewRef()) ? invocation.context().values() : invocation.context().read(rule.property());
    }
    private static void writeOne(CompiledRule rule, RuleViewRegistry.Invocation invocation, Map<String,Object> row) {
        if (rule.property().equals(invocation.key().viewRef())) new ModelContext(row).values().forEach(invocation::write);
        else invocation.write(rule.property(), merge(invocation.context().read(rule.property()), row));
    }
    private static Map<String,Object> merge(Object existing, Map<String,Object> row) {
        Map<String,Object> values = new LinkedHashMap<>();
        if (existing instanceof Map<?,?> map) map.forEach((key,value) -> values.put(String.valueOf(key),value));
        values.putAll(row); return new ModelContext(values).values();
    }
}
