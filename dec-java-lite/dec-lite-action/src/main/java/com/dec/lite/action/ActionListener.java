package com.dec.lite.action;

/** One listener per canonical Business flow, receiving its Action events. Implementations must be thread-safe. */
public interface ActionListener {
    String businessId();
    default void before(ActionListenerContext context) { }
    /** Success only, inside the transaction, before Directory Change and physical commit. */
    default void after(ActionListenerContext context, ActionResult result) { }
}
