package com.example.orderpayment.order.store;

import com.example.orderpayment.payment.entity.PayDetail;
import com.example.orderpayment.order.entity.OrderDetail;
import java.util.function.Function;
import java.util.List;
import com.example.orderpayment.order.dao.OrderDao;
import com.example.orderpayment.order.dao.OrderDetailDao;
import com.example.orderpayment.payment.dao.PayDao;
import com.example.orderpayment.payment.dao.PayDetailDao;
import com.example.orderpayment.user.dao.UserDao;
import org.springframework.jdbc.core.JdbcTemplate;


/** Plain JDBC-backed DataSource adapter created by {@link LocalOrderStoreFactory}. */
public final class LocalOrderStore {
    private final JdbcTemplate jdbc;
    private final OrderDao orderDao;
    private final OrderDetailDao orderDetailDao;
    private final PayDao payDao;
    private final PayDetailDao payDetailDao;
    private final UserDao userDao;
    private final Function<Long, List<OrderDetail>> orderRelations;
    private final Function<Long, List<PayDetail>> paymentRelations;

    LocalOrderStore(JdbcTemplate jdbc, OrderDao orderDao, OrderDetailDao orderDetailDao,
                    PayDao payDao, PayDetailDao payDetailDao, UserDao userDao,
                    Function<Long, List<OrderDetail>> orderRelations,
                    Function<Long, List<PayDetail>> paymentRelations) {
        this.jdbc = jdbc;
        this.orderDao = orderDao;
        this.orderDetailDao = orderDetailDao;
        this.payDao = payDao;
        this.payDetailDao = payDetailDao;
        this.userDao = userDao;
        this.orderRelations = orderRelations;
        this.paymentRelations = paymentRelations;
    }

    /** Lock the order row for a state-changing request within the Spring transaction. */
    public void lockOrder(Long orderId) {
        jdbc.query("SELECT o_id FROM dec_demo_order_info WHERE o_id = ? FOR UPDATE",
                row -> { }, orderId);
    }

    public OrderDao orders() { return orderDao; }
    public OrderDetailDao orderDetails() { return orderDetailDao; }
    public PayDao pays() { return payDao; }
    public PayDetailDao payDetails() { return payDetailDao; }
    public UserDao users() { return userDao; }
    public List<OrderDetail> detailsForOrder(Long id) { return orderRelations.apply(id); }
    public List<PayDetail> detailsForPayment(Long id) { return paymentRelations.apply(id); }
}
