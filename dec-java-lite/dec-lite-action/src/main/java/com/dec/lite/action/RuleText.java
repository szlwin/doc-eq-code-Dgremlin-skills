package com.dec.lite.action;

import java.util.ArrayList;
import java.util.List;

/** Split Rule syntax without splitting quoted strings or nested calls. */
final class RuleText {
    static List<String> split(String source, char separator) {
        List<String> parts = new ArrayList<>(); int start = 0, depth = 0; char quote = 0; boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '(') depth++;
            else if (c == ')') { if (--depth < 0) throw new ActionExecutionException("Unbalanced Rule parentheses"); }
            else if (c == separator && depth == 0) { parts.add(source.substring(start, i).trim()); start = i + 1; }
        }
        if (quote != 0 || depth != 0) throw new ActionExecutionException("Unclosed Rule string or parentheses");
        parts.add(source.substring(start).trim()); return parts;
    }
}
