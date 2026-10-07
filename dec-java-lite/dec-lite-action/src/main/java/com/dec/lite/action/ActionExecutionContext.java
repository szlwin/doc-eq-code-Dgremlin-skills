package com.dec.lite.action;

import com.dec.lite.information.InformationEngine;
import com.dec.lite.information.ModelContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ActionExecutionContext {
    private final InformationEngine informationEngine;
    private final ModelContext model;
    private final Map<String, Object> payload;
    private final Instant deadline;
    private final ActionTransaction transaction;
    private final boolean transactionOwner;
    private final boolean applyDirectoryChange;
    private final SystemAccessPolicy accessPolicy;
    private final Map<String, Object> produced = new LinkedHashMap<>();

    public ActionExecutionContext(InformationEngine informationEngine, ModelContext model,
                                  Map<String, Object> payload, Instant deadline) {
        this(informationEngine, model, payload, deadline, new ActionTransaction() { });
    }
    public ActionExecutionContext(InformationEngine informationEngine, ModelContext model,
                                  Map<String, Object> payload, Instant deadline, ActionTransaction transaction) {
        this(informationEngine, model, payload, deadline, transaction, true, true);
    }
    public ActionExecutionContext(InformationEngine informationEngine, ModelContext model,
                                  Map<String, Object> payload, Instant deadline, ActionTransaction transaction,
                                  boolean transactionOwner, boolean applyDirectoryChange) {
        this(informationEngine, model, payload, deadline, transaction, transactionOwner, applyDirectoryChange, SystemAccessPolicy.allowAll());
    }
    public ActionExecutionContext(InformationEngine informationEngine, ModelContext model,
                                  Map<String, Object> payload, Instant deadline, ActionTransaction transaction,
                                  boolean transactionOwner, boolean applyDirectoryChange, SystemAccessPolicy accessPolicy) {
        this.informationEngine = informationEngine;
        this.model = model;
        this.payload = Map.copyOf(payload == null ? Map.of() : payload);
        this.deadline = deadline;
        this.transaction = transaction == null ? new ActionTransaction() { } : transaction;
        this.transactionOwner = transactionOwner;
        this.applyDirectoryChange = applyDirectoryChange;
        this.accessPolicy = accessPolicy == null ? SystemAccessPolicy.allowAll() : accessPolicy;
    }
    public InformationEngine informationEngine() { return informationEngine; }
    public ModelContext model() { return model; }
    public Map<String, Object> payload() { return payload; }
    public Instant deadline() { return deadline; }
    public ActionTransaction transaction() { return transaction; }
    public boolean transactionOwner() { return transactionOwner; }
    public boolean applyDirectoryChange() { return applyDirectoryChange; }
    public SystemAccessPolicy accessPolicy() { return accessPolicy; }
    public void produce(String ref, Object value) { produced.put(ref, value); }
    public Map<String, Object> mutableProduced() { return produced; }
    public Map<String, Object> produced() { return Map.copyOf(produced); }
}
