package com.dec.lite.information;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record InformationCompilation(
        Map<InformationKey, InformationDefinition> definitions,
        Map<InformationKey, ModelExpressionCompiler.CompiledExpression> modelExpressions,
        Map<InformationKey, InformationExpression> informationExpressions,
        Map<InformationKey, Set<InformationKey>> dependencies,
        Map<InformationKey, Set<InformationKey>> reverseDependencies,
        List<InformationKey> topologicalOrder,
        String graphDigest) {
    public InformationCompilation {
        definitions = Map.copyOf(definitions); modelExpressions = Map.copyOf(modelExpressions); informationExpressions = Map.copyOf(informationExpressions);
        dependencies = freeze(dependencies); reverseDependencies = freeze(reverseDependencies); topologicalOrder = List.copyOf(topologicalOrder);
    }
    private static Map<InformationKey, Set<InformationKey>> freeze(Map<InformationKey, Set<InformationKey>> values) {
        Map<InformationKey, Set<InformationKey>> result = new java.util.LinkedHashMap<>();
        values.forEach((key, dependencies) -> result.put(key, Set.copyOf(dependencies)));
        return Map.copyOf(result);
    }
}
