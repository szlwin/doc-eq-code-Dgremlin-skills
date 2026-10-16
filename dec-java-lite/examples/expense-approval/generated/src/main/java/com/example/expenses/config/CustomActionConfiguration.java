package com.example.expenses.config;

import com.dec.lite.action.CustomAction;
import com.dec.lite.action.CustomActionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers application CustomAction beans. DirectoryWorkflowRunner validates required design bindings. */
@Configuration(proxyBeanMethods = false)
public class CustomActionConfiguration {
    @Bean
    public CustomActionRegistry customActionRegistry(ObjectProvider<CustomAction> actions) {
        return CustomActionRegistry.from(actions.orderedStream().toList());
    }
}
