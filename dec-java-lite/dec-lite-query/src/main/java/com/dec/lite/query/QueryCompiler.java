package com.dec.lite.query;

import com.dec.lite.directory.CaseEdge;
import com.dec.lite.directory.DirectoryDefinition;
import com.dec.lite.directory.DirectoryGraph;
import com.dec.lite.directory.PathPlanner;
import com.dec.lite.information.InformationCompilation;
import com.dec.lite.information.InformationKey;
import com.dec.lite.model.DecProject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Compiles DirectoryQuery without embedding SQL or a database connection. */
public final class QueryCompiler {
    private final DirectoryGraph graph;
    private final InformationCompilation information;
    private final QuerySchema schema;
    public QueryCompiler(DecProject project, DirectoryGraph graph, InformationCompilation information) {
        this.graph = graph; this.information = information; this.schema = QuerySchema.compile(project);
    }
    public QueryPlan compile(DirectoryQuery query) {
        DirectoryDefinition directory = graph.directories().get(query.directory());
        if (directory == null) throw new QueryCompilationException("unknown Directory: " + query.directory());
        List<String> path = new PathPlanner().plan(graph, query.from(), query.directory()).directories();
        QuerySchema.View view = schema.view(directory.modelRef()); ViewJoinPlanner joins = new ViewJoinPlanner(schema, view);
        for (String relation : query.with()) joins.include(relation);
        AtomicInteger numbers = new AtomicInteger();
        InformationPredicateCompiler predicates = new InformationPredicateCompiler(information, joins, view.name(), numbers);
        List<QueryPlan.CaseBranch> branches = branches(directory, query, predicates);
        if (branches.isEmpty()) throw new QueryCompilationException("query has no case branch");
        for (QueryPlan.CaseBranch branch : branches) for (InformationKey key : branch.information()) includeModelRelations(key, view, joins, new LinkedHashSet<>());
        if (query.to() != null && !query.to().equals(query.directory()) && branches.stream().noneMatch(branch -> branch.name().equals(query.to())))
            throw new QueryCompilationException("to must be the target Directory or a selected case: " + query.to());
        List<SqlPredicate> conditions = new ArrayList<>();
        conditions.add(SqlPredicate.or(branches.stream().map(QueryPlan.CaseBranch::predicate).toList()));
        for (DirectoryQuery.Filter filter : query.filters()) {
            SqlPredicate.Field field = joins.field(filter.path());
            if (filter.operator() == DirectoryQuery.Operator.IN) {
                List<?> values = (List<?>) filter.value();
                if (values.isEmpty()) throw new QueryCompilationException("whereIn values cannot be empty");
                List<TypedParameter> valuesTyped = new ArrayList<>();
                for (Object value : values) {
                    if (value == null) throw new QueryCompilationException("whereIn cannot contain null");
                    valuesTyped.add(new TypedParameter("where" + numbers.incrementAndGet(), field.type(), value, filter.sensitive()));
                }
                conditions.add(new SqlPredicate.Compare(field, "IN", valuesTyped));
            } else conditions.add(new SqlPredicate.Compare(field, "=", filter.value() == null ? List.of() :
                    List.of(new TypedParameter("where" + numbers.incrementAndGet(), field.type(), filter.value(), filter.sensitive()))));
        }
        QuerySchema.Data root = joins.root();
        boolean runtimeOnly = branches.stream().anyMatch(QueryPlan.CaseBranch::postFilter);
        if (runtimeOnly && query.offset() + query.size() > query.candidateLimit())
            throw new QueryCompilationException("runtime-only query page exceeds candidateLimit");
        List<String> trace = new ArrayList<>();
        trace.add("path=" + path);
        trace.add("selectedCases=" + branches.stream().map(QueryPlan.CaseBranch::name).toList());
        trace.add("information=" + branches.stream().map(branch -> branch.information().toString()).toList());
        trace.add("informationPredicates=" + branches.stream().map(branch -> branch.name() + ":" + branch.predicate()).toList());
        trace.add("joins=" + joins.joins().stream().map(JoinPlan::path).toList());
        trace.add("pushdown=" + !conditions.get(0).equals(SqlPredicate.all()));
        trace.add("postFilter=" + runtimeOnly);
        trace.add("route=" + schema.route(root).connection());
        SqlPredicate predicate = SqlPredicate.and(conditions);
        return new QueryPlan(directory.name(), view.name(), root.table(), root.column(root.idProperty()),
                joins.projections(), joins.joins(), predicate, branches,
                branches.stream().map(QueryPlan.CaseBranch::name).toList(), parameters(predicate), root.column(root.idProperty()),
                query.offset(), query.size(), query.candidateLimit(),
                new AssemblyPlan("__dec_id", joins.joins().stream().filter(JoinPlan::many).map(JoinPlan::path).toList()),
                schema.route(root), runtimeOnly, trace);
    }
    private List<QueryPlan.CaseBranch> branches(DirectoryDefinition directory, DirectoryQuery query,
                                                InformationPredicateCompiler compiler) {
        List<QueryPlan.CaseBranch> result = new ArrayList<>();
        if (directory.result()) {
            List<CaseEdge> cases = graph.caseEdges().stream().filter(edge -> edge.parent().equals(directory.name())).toList();
            String caseName = query.caseName() != null ? query.caseName() : query.to() != null && !query.to().equals(directory.name()) ? query.to() : null;
            if (caseName != null && cases.stream().noneMatch(edge -> edge.target().equals(caseName))) throw new QueryCompilationException("unknown case for " + directory.name() + ": " + caseName);
            for (CaseEdge edge : cases) {
                if (caseName != null && !caseName.equals(edge.target())) continue;
                result.add(branch(edge.target(), List.of(directory.informationRef(), edge.informationRef()), compiler));
            }
        } else {
            if (query.caseName() != null) throw new QueryCompilationException("eq is only valid for result Directory queries");
            List<InformationKey> keys = new ArrayList<>(); keys.add(directory.informationRef());
            graph.caseEdges().stream().filter(edge -> edge.target().equals(directory.name())).forEach(edge -> keys.add(edge.informationRef()));
            result.add(branch(directory.name(), keys, compiler));
        }
        return result;
    }
    private static QueryPlan.CaseBranch branch(String name, List<InformationKey> keys, InformationPredicateCompiler compiler) {
        List<SqlPredicate> conditions = new ArrayList<>(); boolean post = false;
        for (InformationKey key : keys) {
            InformationPredicateCompiler.Result result = compiler.compile(key);
            if (result.coverage() == InformationPredicateCompiler.Coverage.UNQUERYABLE) throw new QueryCompilationException("Information cannot be queried: " + key);
            conditions.add(result.predicate()); post |= result.coverage() != InformationPredicateCompiler.Coverage.FULL;
        }
        return new QueryPlan.CaseBranch(name, keys, SqlPredicate.and(conditions), post);
    }
    private void includeModelRelations(InformationKey key, QuerySchema.View view, ViewJoinPlanner joins, Set<InformationKey> visited) {
        if (!visited.add(key)) return;
        var expression = information.modelExpressions().get(key);
        var definition = information.definitions().get(key);
        if (expression != null && definition != null && view.name().equals(definition.viewRef())) {
            for (String readPath : expression.readPaths()) {
                Map<String, QuerySchema.Property> properties = view.properties(); String prefix = "";
                for (String part : readPath.split("\\.")) {
                    QuerySchema.Property property = properties.get(part);
                    if (property == null || !property.isRelation()) break;
                    prefix = prefix.isEmpty() ? part : prefix + "." + part;
                    joins.include(prefix); properties = property.children();
                }
            }
        }
        for (InformationKey dependency : information.dependencies().getOrDefault(key, Set.of()))
            includeModelRelations(dependency, view, joins, visited);
    }
    private static List<TypedParameter> parameters(SqlPredicate predicate) {
        List<TypedParameter> result = new ArrayList<>(); collect(predicate, result); return result;
    }
    private static void collect(SqlPredicate predicate, List<TypedParameter> result) {
        if (predicate instanceof SqlPredicate.Compare compare) result.addAll(compare.parameters());
        if (predicate instanceof SqlPredicate.Group group) group.children().forEach(child -> collect(child, result));
    }
}
