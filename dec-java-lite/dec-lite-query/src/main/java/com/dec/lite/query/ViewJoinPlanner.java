package com.dec.lite.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves View paths to Data columns and explicit LEFT JOINs. */
public final class ViewJoinPlanner {
    private final QuerySchema schema;
    private final QuerySchema.View view;
    private final QuerySchema.Data root;
    private final Map<String, JoinPlan> joins = new LinkedHashMap<>();
    private final Map<String, QuerySchema.Data> dataByPath = new LinkedHashMap<>();
    public ViewJoinPlanner(QuerySchema schema, QuerySchema.View view) {
        this.schema = schema; this.view = view; this.root = schema.data(view.mainData()); dataByPath.put("", root);
    }
    public QuerySchema.Data root() { return root; }
    public List<JoinPlan> joins() { return List.copyOf(joins.values()); }
    public SqlPredicate.Field field(String path) {
        String[] parts = path.split("\\."); String prefix = ""; Map<String, QuerySchema.Property> properties = view.properties();
        for (int i = 0; i < parts.length - 1; i++) {
            QuerySchema.Property relation = properties.get(parts[i]);
            if (relation == null || !relation.isRelation()) throw new QueryCompilationException("unknown View relation: " + path);
            prefix = prefix.isEmpty() ? parts[i] : prefix + "." + parts[i]; addJoin(prefix, relation);
            properties = relation.children();
        }
        QuerySchema.Property property = properties.get(parts[parts.length - 1]);
        if (property == null || property.isRelation() || property.ref() == null) throw new QueryCompilationException("unknown scalar View property: " + path);
        QuerySchema.Data data = dataByPath.get(prefix);
        return new SqlPredicate.Field(alias(prefix), data.column(property.ref()), path, data.types().get(property.ref()));
    }
    public void include(String path) {
        String prefix = ""; Map<String, QuerySchema.Property> properties = view.properties();
        for (String part : path.split("\\.")) {
            QuerySchema.Property relation = properties.get(part);
            if (relation == null || !relation.isRelation()) throw new QueryCompilationException("unknown View relation: " + path);
            prefix = prefix.isEmpty() ? part : prefix + "." + part; addJoin(prefix, relation); properties = relation.children();
        }
    }
    public List<Projection> projections() {
        List<Projection> result = new ArrayList<>();
        addProjections(result, "", view.properties());
        return result;
    }
    private void addProjections(List<Projection> result, String prefix, Map<String, QuerySchema.Property> properties) {
        QuerySchema.Data data = dataByPath.get(prefix);
        for (QuerySchema.Property property : properties.values()) {
            String path = prefix.isEmpty() ? property.name() : prefix + "." + property.name();
            if (property.isRelation()) { if (joins.containsKey(path)) addProjections(result, path, property.children()); }
            else if (property.ref() != null) result.add(new Projection(path, alias(prefix), data.column(property.ref()), resultAlias(path), data.types().get(property.ref())));
        }
        if (!prefix.isEmpty()) {
            result.add(new Projection(prefix + ".__dec_id", alias(prefix), data.column(data.idProperty()), resultAlias(prefix + ".__dec_id"), data.types().get(data.idProperty())));
        }
    }
    private void addJoin(String path, QuerySchema.Property relation) {
        if (joins.containsKey(path)) return;
        if (!List.of("one-to-one", "one-to-many").contains(relation.relation())) throw new QueryCompilationException("unsupported View relation: " + relation.relation());
        String parentPath = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
        QuerySchema.Data parent = dataByPath.get(parentPath); if (parent == null) throw new QueryCompilationException("missing parent join for " + path);
        QuerySchema.Data child = schema.data(relation.data());
        if (!schema.route(parent).connection().equals(schema.route(child).connection())) throw new QueryCompilationException("cross-connection View join is unsupported: " + path);
        String alias = alias(path);
        joins.put(path, new JoinPlan(path, alias, alias(parentPath), child.table(), parent.column(relation.relKey()),
                child.column(relation.key()), "one-to-many".equals(relation.relation()), child.column(child.idProperty())));
        dataByPath.put(path, child);
    }
    private static String alias(String path) { return path.isEmpty() ? "t0" : "t" + path.replace('.', '_'); }
    static String resultAlias(String path) { return path.replace('.', '_') + "__dec"; }
}
