package com.example.orderpayment.order.workflow;

import com.dec.lite.action.DataSourceAdapter;
import com.example.orderpayment.order.store.LocalOrderStoreFactory;
import com.example.orderpayment.order.view.OrderViewAssembler;
import com.example.orderpayment.payment.adapter.LocalPaymentErrorAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application-owned injection of DEC DataSource implementations; add named beans for other providers. */
@Configuration(proxyBeanMethods = false)
public class OrderDataSourceConfiguration {
    @Bean
    public DataSourceAdapter localOrderDataSource(LocalOrderStoreFactory stores, LocalPaymentErrorAdapter errors) {
        return new DataSourceAdapter("local", new LocalRuleAdapter(stores.create(),
                new DecViewModelBridge(), new OrderViewAssembler(), errors));
    }
}
