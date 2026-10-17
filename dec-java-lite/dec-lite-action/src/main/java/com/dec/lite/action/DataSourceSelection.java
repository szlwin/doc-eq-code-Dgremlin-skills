package com.dec.lite.action;

import com.dec.lite.information.RecognitionResult;
import com.dec.lite.information.RuleViewRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Selected logical DataSource -> implementation mapping for one execution; no global/thread-local switches. */
public final class DataSourceSelection implements DataOperationAdapter {
    private final Map<String, String> names;
    private final Map<String, DataOperationAdapter> routes;
    DataSourceSelection(Map<String, String> names, Map<String, DataOperationAdapter> routes) {
        this.names = Map.copyOf(names); this.routes = Map.copyOf(routes);
    }
    public Map<String, String> implementations() { return names; }
    /** Validate before opening a transaction or executing a Rule, including unselected case branches. */
    public void validate(Collection<CompiledRuleView> views) {
        validate(views, java.util.List.of(), null);
    }
    public DataOperationAdapter adapter(String logical) {
        DataOperationAdapter adapter = logical == null ? null : routes.get(logical);
        if (adapter == null) throw new IllegalArgumentException("No DataSource implementation selected: " + logical);
        return adapter;
    }
    public void validate(Collection<CompiledRuleView> views, Collection<ActionDefinition> actions, String defaultSource) {
        validate(views, actions, defaultSource, views.stream().map(CompiledRuleView::key).collect(java.util.stream.Collectors.toSet()));
    }
    public void validate(Collection<CompiledRuleView> views, Collection<ActionDefinition> actions, String defaultSource,
                         Set<com.dec.lite.information.RuleViewKey> recognitionRules) {
        Set<String> declared = new HashSet<>();
        if (defaultSource != null) { declared.add(defaultSource); adapter(defaultSource); }
        for (CompiledRuleView view : views) for (CompiledRule rule : view.rules()) {
            String source = rule.dataSource() != null ? rule.dataSource() : defaultSource;
            if (source != null) declared.add(source);
            if (rule.external()) {
                // Action-only rules may get their entire route from their Action declaration.
                var callers = actions.stream().filter(a -> a.isRuleView()
                        && a.systemRef().equals(view.key().system()) && a.ruleRef().equals(view.key().ruleRef())).toList();
                if (callers.isEmpty() || recognitionRules.contains(view.key())) adapter(source);
                for (ActionDefinition action : callers) {
                    String effective = action.resolveDataSource(rule.dataSource());
                    adapter(effective); declared.add(effective);
                }
            }
        }
        for (ActionDefinition action : actions) {
            if (action.dataSource() != null) { declared.add(action.dataSource()); adapter(action.dataSource()); }
            if (action.businessDataSource() != null) { declared.add(action.businessDataSource()); adapter(action.businessDataSource()); }
        }
        for (String logical : names.keySet()) if (!declared.contains(logical))
            throw new IllegalArgumentException("No design references logical DataSource: " + logical);
    }
    @Override public java.util.List<Map<String, Object>> read(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        return adapter(rule.dataSource()).read(rule, invocation);
    }
    @Override public RecognitionResult execute(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        return adapter(rule.dataSource()).execute(rule, invocation);
    }
}
