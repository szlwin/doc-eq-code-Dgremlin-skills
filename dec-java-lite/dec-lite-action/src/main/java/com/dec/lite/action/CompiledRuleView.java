package com.dec.lite.action;

import com.dec.lite.information.RuleViewKey;
import java.util.List;

public record CompiledRuleView(RuleViewKey key, String designId, List<CompiledRule> rules) {
    public CompiledRuleView { rules = List.copyOf(rules); }
}
