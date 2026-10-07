package com.dec.lite.query;

import com.dec.lite.information.InformationKey;

import java.util.List;

/** Portable query plan; no SQL dialect or connection object is stored here. */
public record QueryPlan(String directory, String rootModel, String rootTable, String rootIdColumn,
                        List<Projection> projections, List<JoinPlan> joins, SqlPredicate predicate,
                        List<CaseBranch> branches, List<String> selectedCases,
                        List<TypedParameter> parameters, String orderBy, int offset, int size,
                        int candidateLimit, AssemblyPlan assembly, ConnectionRoute route, boolean runtimeOnly,
                        List<String> trace) {
    public QueryPlan {
        projections = List.copyOf(projections); joins = List.copyOf(joins); branches = List.copyOf(branches);
        selectedCases = List.copyOf(selectedCases); parameters = List.copyOf(parameters); trace = List.copyOf(trace);
    }
    /** Each branch is a case of the result Directory, or a single ordinary Directory. */
    public record CaseBranch(String name, List<InformationKey> information, SqlPredicate predicate, boolean postFilter) {
        public CaseBranch { information = List.copyOf(information); }
    }
}
