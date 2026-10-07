package com.dec.lite.information;

import java.util.Objects;

/** Immutable runtime dependencies shared by one InformationEngine instance. */
public record InformationEngineContext(
        InformationCompilation compilation,
        RuleViewRegistry ruleViews) {
    public InformationEngineContext {
        compilation = Objects.requireNonNull(compilation, "compilation");
        ruleViews = Objects.requireNonNull(ruleViews, "ruleViews");
    }
}
