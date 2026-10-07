package com.dec.lite.query;

import com.dec.lite.information.ModelContext;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Folds joined rows into root models, de-duplicating one-to-many children by Data ID. */
public final class ResultAssembler {
    public Map<String, ModelContext> assemble(QueryPlan plan, List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> roots = new LinkedHashMap<>();
        IdentityHashMap<Map<String, Object>, Map<String, Map<String, Object>>> children = new IdentityHashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get(plan.assembly().rootIdAlias()); if (id == null) throw new QueryExecutionException("detail row has no root ID");
            Map<String, Object> root = roots.computeIfAbsent(String.valueOf(id), ignored -> new LinkedHashMap<>());
            Map<String, Map<String, Object>> objects = new LinkedHashMap<>(); objects.put("", root);
            for (JoinPlan join : plan.joins()) {
                String parentPath = join.path().contains(".") ? join.path().substring(0, join.path().lastIndexOf('.')) : "";
                Map<String, Object> parent = objects.get(parentPath);
                if (parent == null) continue;
                String property = join.path().substring(join.path().lastIndexOf('.') + 1);
                Object childId = row.get(ViewJoinPlanner.resultAlias(join.path() + ".__dec_id"));
                if (childId == null) { if (join.many()) parent.computeIfAbsent(property, ignored -> new ArrayList<>()); continue; }
                Map<String, Object> child;
                if (join.many()) {
                    Map<String, Map<String, Object>> byId = children.computeIfAbsent(parent, ignored -> new LinkedHashMap<>());
                    String key = join.path() + "#" + childId;
                    child = byId.get(key);
                    if (child == null) {
                        child = new LinkedHashMap<>(); byId.put(key, child);
                        @SuppressWarnings("unchecked") List<Map<String, Object>> list = (List<Map<String, Object>>) parent.computeIfAbsent(property, ignored -> new ArrayList<>());
                        list.add(child);
                    }
                } else {
                    @SuppressWarnings("unchecked") Map<String, Object> existing = (Map<String, Object>) parent.get(property);
                    child = existing == null ? new LinkedHashMap<>() : existing; parent.put(property, child);
                }
                objects.put(join.path(), child);
            }
            for (Projection projection : plan.projections()) {
                if (projection.path().endsWith(".__dec_id")) continue;
                String parentPath = projection.path().contains(".") ? projection.path().substring(0, projection.path().lastIndexOf('.')) : "";
                Map<String, Object> parent = objects.get(parentPath);
                if (parent != null) parent.put(projection.path().substring(projection.path().lastIndexOf('.') + 1), row.get(projection.resultAlias()));
            }
        }
        Map<String, ModelContext> result = new LinkedHashMap<>();
        roots.forEach((id, values) -> result.put(id, new ModelContext(values)));
        return result;
    }
}
