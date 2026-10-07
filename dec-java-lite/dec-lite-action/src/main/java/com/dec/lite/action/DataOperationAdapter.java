package com.dec.lite.action;

import com.dec.lite.information.RecognitionResult;
import com.dec.lite.information.RuleViewRegistry;

/** External DataSource operation boundary; implementations use Invocation.write/produce. */
@FunctionalInterface
public interface DataOperationAdapter {
    RecognitionResult execute(CompiledRule rule, RuleViewRegistry.Invocation invocation);
}
