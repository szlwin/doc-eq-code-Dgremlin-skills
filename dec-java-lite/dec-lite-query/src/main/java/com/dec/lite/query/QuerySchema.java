package com.dec.lite.query;

import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data, View and connection metadata compiled from canonical DEC YAML. */
public final class QuerySchema {
    public record Data(String name, String table, String dataSource, String idProperty,
                       Map<String, String> columns, Map<String, String> types) {
        public Data { columns = Map.copyOf(columns); types = Map.copyOf(types); }
        public String column(String property) { String value = columns.get(property); if (value == null) throw new QueryCompilationException("unknown Data property " + name + "." + property); return value; }
    }
    public record Property(String name, String ref, String relation, String data, String key, String relKey,
                           Map<String, Property> children) {
        public Property { children = Map.copyOf(children); }
        public boolean isRelation() { return relation != null; }
    }
    public record View(String name, String mainData, Map<String, Property> properties) {
        public View { properties = Map.copyOf(properties); }
    }
    private final Map<String, Data> data;
    private final Map<String, View> views;
    private final Map<String, ConnectionRoute> routes;
    private QuerySchema(Map<String, Data> data, Map<String, View> views, Map<String, ConnectionRoute> routes) {
        this.data = Map.copyOf(data); this.views = Map.copyOf(views); this.routes = Map.copyOf(routes);
    }
    public Data data(String name) { Data value = data.get(name); if (value == null) throw new QueryCompilationException("unknown Data: " + name); return value; }
    public View view(String name) { View value = views.get(name); if (value == null) throw new QueryCompilationException("unknown View: " + name); return value; }
    public ConnectionRoute route(Data data) {
        ConnectionRoute value = routes.get(data.dataSource());
        if (value == null) throw new QueryCompilationException("no connection route for DataSource " + data.dataSource());
        if (!"MySQL".equalsIgnoreCase(value.type())) throw new QueryCompilationException("SQL query DataSource must be MySQL: " + data.dataSource() + " type=" + value.type());
        return value;
    }
    public static QuerySchema compile(DecProject project) {
        Map<String, Data> data = new LinkedHashMap<>(); Map<String, View> views = new LinkedHashMap<>();
        Map<String, String> sourceTypes = new LinkedHashMap<>(); Map<String, String> sourceRoutes = new LinkedHashMap<>();
        for (DecDocument document : project.getDocuments()) {
            Map<String, Object> root = document.root();
            if (document.kind() == DecKind.CONFIG) {
                Map<String, Object> sourceInfo = map(root.get("dataSourceInfo"), "dataSourceInfo");
                for (Object raw : list(sourceInfo.get("dataSources"), "dataSources")) {
                    Map<String, Object> value = map(raw, "dataSource"); sourceTypes.put(text(value, "name"), text(value, "type"));
                }
                Map<String, Object> connectionInfo = map(root.get("connectionInfo"), "connectionInfo");
                for (Object raw : list(connectionInfo.get("connections"), "connections")) {
                    Map<String, Object> value = map(raw, "connection"); String name = text(value, "name");
                    for (Object source : list(value.get("dataSources"), "connection.dataSources")) {
                        String prior = sourceRoutes.putIfAbsent(String.valueOf(source), name);
                        if (prior != null && !prior.equals(name)) throw new QueryCompilationException("ambiguous connection route for DataSource " + source);
                    }
                }
            }
            if (document.kind() == DecKind.DATA) for (Object raw : list(root.get("datas"), "datas")) {
                Map<String, Object> value = map(raw, "data"); String name = text(value, "name");
                Map<String, Object> properties = map(value.get("properties"), "data.properties");
                Map<String, String> types = new LinkedHashMap<>();
                properties.forEach((property, metadata) -> types.put(property, text(map(metadata, "property"), "type")));
                List<?> tables = list(value.get("tables"), "data.tables");
                if (tables.size() != 1) throw new QueryCompilationException("queryable Data must have exactly one table: " + name);
                Map<String, Object> table = map(tables.get(0), "table");
                Map<String, String> columns = new LinkedHashMap<>();
                map(table.get("columns"), "table.columns").forEach((column, target) -> {
                    String property = target instanceof Map<?, ?> ? text(map(target, "column"), "ref") : String.valueOf(target);
                    if (columns.putIfAbsent(property, column) != null) throw new QueryCompilationException("duplicate column mapping: " + name + "." + property);
                });
                String idProperty = columns.entrySet().stream().filter(entry -> entry.getValue().equals(text(table, "key"))).map(Map.Entry::getKey).findFirst()
                        .orElseThrow(() -> new QueryCompilationException("table key has no property mapping: " + name));
                if (data.putIfAbsent(name, new Data(name, text(table, "name"), text(table, "dataSource"), idProperty, columns, types)) != null)
                    throw new QueryCompilationException("duplicate Data: " + name);
            }
            if (document.kind() == DecKind.VIEW) for (Object raw : list(root.get("views"), "views")) {
                Map<String, Object> value = map(raw, "view"); String name = text(value, "name");
                if (views.putIfAbsent(name, new View(name, text(value, "targetMain"), properties(map(value.get("properties"), "view.properties")))) != null)
                    throw new QueryCompilationException("duplicate View: " + name);
            }
        }
        Map<String, ConnectionRoute> routes = new LinkedHashMap<>();
        for (Data value : data.values()) {
            String source = value.dataSource(); String connection = sourceRoutes.get(source); String type = sourceTypes.get(source);
            if (connection != null && type != null) routes.put(source, new ConnectionRoute(connection, source, type));
        }
        return new QuerySchema(data, views, routes);
    }
    private static Map<String, Property> properties(Map<String, Object> raw) {
        Map<String, Property> result = new LinkedHashMap<>();
        raw.forEach((name, metadata) -> {
            Map<String, Object> value = map(metadata, "view property");
            String relation = optional(value, "relation");
            result.put(name, new Property(name, optional(value, "ref"), relation, optional(value, "data"),
                    optional(value, "key"), optional(value, "relKey"),
                    relation == null ? Map.of() : properties(map(value.get("properties"), "relation.properties"))));
        });
        return result;
    }
    static Map<String, Object> map(Object raw, String context) {
        if (!(raw instanceof Map<?, ?> source)) throw new QueryCompilationException(context + " must be a map");
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> { if (!(key instanceof String)) throw new QueryCompilationException(context + " keys must be strings"); result.put((String) key, value); });
        return result;
    }
    static List<?> list(Object raw, String context) { if (!(raw instanceof List<?> value)) throw new QueryCompilationException(context + " must be a list"); return value; }
    static String text(Map<String, Object> map, String field) { String value = optional(map, field); if (value == null) throw new QueryCompilationException("requires " + field); return value; }
    static String optional(Map<String, Object> map, String field) { Object value = map.get(field); return value instanceof String text && !text.isBlank() ? text : null; }
}
