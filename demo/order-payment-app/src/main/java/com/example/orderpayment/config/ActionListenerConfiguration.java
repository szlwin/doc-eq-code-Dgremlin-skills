package com.example.orderpayment.config;

import com.dec.lite.action.ActionListener;
import com.dec.lite.action.ActionListenerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Collects one listener per Business Design ID; pass this registry to DirectoryWorkflowRunner. */
@Configuration(proxyBeanMethods = false)
public class ActionListenerConfiguration {
    @Bean
    public ActionListenerRegistry actionListenerRegistry(ObjectProvider<ActionListener> listeners) {
        return ActionListenerRegistry.from(listeners.stream().toList());
    }
}
