package com.dec.lite.action;

import com.dec.lite.information.ModelContext;
import java.util.Objects;
import java.util.function.Function;

/** Request-scoped typed projection of the flow's modelRef; no mutable View is cached. */
public record ActionViewBinding(String viewRef, Class<?> viewType, Function<ModelContext, ?> decoder) {
    public ActionViewBinding {
        if (viewRef == null || viewRef.isBlank()) throw new IllegalArgumentException("View reference is required");
        Objects.requireNonNull(viewType, "View Java type");
        Objects.requireNonNull(decoder, "View decoder");
    }
    public <T> T read(Class<T> type, ModelContext model) {
        if (!Objects.requireNonNull(type, "View type").equals(viewType))
            throw new IllegalArgumentException("Flow View " + viewRef + " is " + viewType.getName() + ", not " + type.getName());
        Object value = decoder.apply(model.copy());
        if (!type.isInstance(value)) throw new IllegalStateException("View decoder returned an invalid " + viewRef);
        return type.cast(value);
    }
}
