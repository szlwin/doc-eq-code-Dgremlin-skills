package com.dec.lite.action;

import com.dec.lite.information.RuleViewKey;
import com.dec.lite.information.TransactionScope;
import com.dec.lite.model.CanonicalValues;
import java.util.List;
import java.util.Map;

/** DataSource driver contract, corresponding to legacy DataConnection.execute(ExecuteParam).
 * Drivers translate design commands/ORM properties and bind parameters; they must join the supplied transaction. */
@FunctionalInterface
public interface RuleDataAccess {
    Result execute(Request request);

    record Request(CompiledRule rule, String operation, RuleViewKey view,
                   Map<String,Object> model, Map<String,Object> payload, TransactionScope transaction) {
        public Request { model = CanonicalValues.map(model); payload = CanonicalValues.map(payload); }
        public Object target() {
            return rule.property() != null && rule.property().equals(view.viewRef())
                    ? model : new com.dec.lite.information.ModelContext(model).read(rule.property() == null ? "" : rule.property());
        }
    }
    record Result(List<Map<String,Object>> rows, int affectedRows) {
        public Result {
            if (rows == null) throw new IllegalArgumentException("DataSource rows are required (empty is allowed)");
            if (rows.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("null DataSource row");
            rows = rows.stream().map(CanonicalValues::map).toList();
            if (affectedRows < 0) throw new IllegalArgumentException("negative affectedRows");
        }
        public static Result read(List<Map<String,Object>> rows) { return new Result(rows, 0); }
        public static Result written(int count) { return new Result(List.of(), count); }
    }
}
