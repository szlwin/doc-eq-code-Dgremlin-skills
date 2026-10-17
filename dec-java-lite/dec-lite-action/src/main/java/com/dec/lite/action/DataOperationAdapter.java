package com.dec.lite.action;

import com.dec.lite.information.RecognitionResult;
import com.dec.lite.information.RuleViewRegistry;

/** External DataSource operation boundary; implementations use Invocation.write/produce. */
@FunctionalInterface
public interface DataOperationAdapter {
    /** Read-only detached rows for checkData/checkDataPattern. Legacy write-only adapters fail explicitly. */
    default java.util.List<java.util.Map<String, Object>> read(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        throw new ActionExecutionException("DataSource adapter does not implement read for " + rule.type() + ": " + rule.id());
    }
    RecognitionResult execute(CompiledRule rule, RuleViewRegistry.Invocation invocation);
}
