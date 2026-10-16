package com.example.orderpayment.order.store;

import com.example.orderpayment.payment.mapper.LocalPayDetailMapperAdapter;
import com.example.orderpayment.order.mapper.LocalOrderDetailMapperAdapter;
import com.example.orderpayment.order.dao.OrderDao;
import com.example.orderpayment.order.dao.OrderDetailDao;
import com.example.orderpayment.payment.dao.PayDao;
import com.example.orderpayment.payment.dao.PayDetailDao;
import com.example.orderpayment.user.dao.UserDao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Composition root for the local DataSource adapter.
 *
 * <p>The generated DAOs and JDBC connection pool are Spring dependencies;
 * {@link LocalOrderStore} is a plain object. Its state is stored in MySQL.</p>
 */
@Component
public final class LocalOrderStoreFactory {
    private final LocalOrderStore store;

    public LocalOrderStoreFactory(JdbcTemplate jdbc,
                                  OrderDao orderDao,
                                  OrderDetailDao orderDetailDao,
                                  PayDao payDao,
                                  PayDetailDao payDetailDao,
                                  UserDao userDao,
                                  LocalOrderDetailMapperAdapter orderRelations,
                                  LocalPayDetailMapperAdapter paymentRelations) {
        this.store = new LocalOrderStore(jdbc, orderDao, orderDetailDao,
                payDao, payDetailDao, userDao, orderRelations::selectByOrderId, paymentRelations::selectByPayId);
    }

    /**
     * Return the factory-owned store. The returned object is not a Spring bean;
     * callers must obtain it from this factory instead of autowiring the store.
     */
    public LocalOrderStore create() {
        return store;
    }
}
