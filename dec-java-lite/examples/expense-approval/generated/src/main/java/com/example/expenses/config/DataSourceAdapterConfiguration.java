package com.example.expenses.config;

import com.dec.lite.action.DataSourceAdapter;
import com.dec.lite.action.DataSourceAdapterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Collects manually supplied DEC adapter implementations; execution chooses logical DataSource mappings. */
@Configuration(proxyBeanMethods = false)
public class DataSourceAdapterConfiguration {
    @Bean
    public DataSourceAdapterRegistry dataSourceAdapterRegistry(ObjectProvider<DataSourceAdapter> adapters) {
        return DataSourceAdapterRegistry.from(adapters.stream().toList());
    }
}
