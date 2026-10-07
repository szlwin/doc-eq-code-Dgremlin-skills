package com.dec.lite.action;

import com.dec.lite.information.InformationKey;
import com.dec.lite.information.RecognitionResult;

public final class ProduceVerifier {
    public void verify(ActionDefinition action, ActionResult result, ActionExecutionContext context) {
        for (ProduceDefinition produce : action.produces()) {
            Object value = result.producedData().get(produce.ref());
            if (value == null && !result.producedData().containsKey(produce.ref())) {
                if (produce.required()) throw new ProduceExecutionException("required Produce is missing: " + produce.ref());
                continue;
            }
            if (produce.multiplicity() > 1) {
                if (!(value instanceof java.util.Collection<?> collection) || collection.size() < produce.multiplicity()) {
                    throw new ProduceExecutionException("Produce multiplicity is not satisfied: " + produce.ref());
                }
            }
            if (produce.type() != null && !produce.type().isBlank() && value != null) {
                String actual = value instanceof java.util.Collection<?> ? "collection" : value.getClass().getSimpleName();
                if (!produce.type().equalsIgnoreCase(actual)) {
                    throw new ProduceExecutionException("Produce type mismatch for " + produce.ref() + ": expected " + produce.type() + ", actual " + actual);
                }
            }
            if (produce.informationRef() != null) {
                RecognitionResult recognition = context.informationEngine().evaluate(produce.informationRef(), context.model());
                if (recognition.status() != RecognitionResult.Status.TRUE) {
                    throw new ProduceExecutionException("Produce Information is not TRUE: " + produce.informationRef() + " (" + recognition.status() + ")");
                }
            }
        }
    }
}
