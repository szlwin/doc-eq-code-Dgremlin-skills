package com.dec.lite.query;

import com.dec.lite.action.CompiledRuleView;
import com.dec.lite.action.DataOperationAdapter;
import com.dec.lite.action.RuleViewCompiler;
import com.dec.lite.directory.DirectoryGraphCompiler;
import com.dec.lite.information.InformationCompilation;
import com.dec.lite.information.InformationEngine;
import com.dec.lite.information.InformationEngineContext;
import com.dec.lite.information.InformationParser;
import com.dec.lite.information.RuleViewKey;
import com.dec.lite.model.DecProject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Compile one Business once; bind evaluators, adapters and query state separately for every call. */
public final class BusinessQueryRuntime {
    private final QueryCompiler compiler;
    private final InformationCompilation information;
    private final Map<RuleViewKey, CompiledRuleView> rules;
    private final Set<RuleViewKey> recognitionRules;
    private final String defaultDataSource;

    public BusinessQueryRuntime(DecProject project, BusinessQueryNames names) {
        information = new InformationParser().parse(project);
        compiler = new QueryCompiler(project, new DirectoryGraphCompiler().compile(project, information), information, names);
        rules = new RuleViewCompiler().compile(project);
        recognitionRules = information.definitions().values().stream().filter(d -> d.ruleRef() != null)
                .map(d -> new RuleViewKey(d.key().system(), d.viewRef(), d.ruleRef())).collect(Collectors.toUnmodifiableSet());
        defaultDataSource = RuleViewCompiler.businessDataSource(project);
    }

    public QueryPlan plan(BusinessQuery query) { return compiler.compile(query); }

    /** Executor owns connection routing/transactions. Null adapter rejects external Rule operations. */
    public QueryResult run(BusinessQuery query, QueryExecutor executor, SqlTranslator translator, DataOperationAdapter adapter) {
        QueryPlan plan = plan(query);
        var registry = new RuleViewCompiler().bind(rules, adapter, List.of(), defaultDataSource, recognitionRules).freeze();
        var engine = new InformationEngine(new InformationEngineContext(information, registry));
        return new QueryRunner(executor, translator, engine).run(plan);
    }
}
