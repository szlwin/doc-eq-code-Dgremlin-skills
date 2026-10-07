package com.example.orderpayment.demo;

import com.example.orderpayment.dao.OrderDao;
import com.example.orderpayment.dao.OrderDetailDao;
import com.example.orderpayment.dao.PayDao;
import com.example.orderpayment.dao.PayDetailDao;
import com.example.orderpayment.dao.UserDao;
import com.example.orderpayment.entity.Order;
import com.example.orderpayment.entity.OrderDetail;
import com.example.orderpayment.entity.Pay;
import com.example.orderpayment.entity.PayDetail;
import com.example.orderpayment.entity.User;
import com.example.orderpayment.enums.PayResultCode;
import com.example.orderpayment.mapper.OrderMapper;
import com.example.orderpayment.mapper.OrderDetailMapper;
import com.example.orderpayment.mapper.PayMapper;
import com.example.orderpayment.mapper.PayDetailMapper;
import com.example.orderpayment.mapper.UserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Local typed DataSource adapter. Generated DAOs call these Mapper implementations. */
@Component
public final class LocalOrderStore {
    private Table<Order> orders = new Table<>(Order::getId, Order::new);
    private Table<OrderDetail> orderDetails = new Table<>(OrderDetail::getId, OrderDetail::new);
    private Table<Pay> pays = new Table<>(Pay::getId, Pay::new);
    private Table<PayDetail> payDetails = new Table<>(PayDetail::getId, PayDetail::new);
    private Table<User> users = new Table<>(User::getId, User::new);
    private final Map<Long, String> directories = new LinkedHashMap<>();
    private final List<Long> smsNotifications = new ArrayList<>();
    private final List<PaymentError> paymentErrors = new ArrayList<>();

    public synchronized UnitOfWork begin() { return new UnitOfWork(); }
    public synchronized String directory(Long orderId) { return directories.get(orderId); }
    public synchronized List<Long> smsNotifications() { return List.copyOf(smsNotifications); }
    public synchronized List<PaymentError> paymentErrors() { return List.copyOf(paymentErrors); }

    public record PaymentError(Integer orderId, PayResultCode resultCode) { }

    public final class UnitOfWork {
        private final Table<Order> stagedOrders;
        private final Table<OrderDetail> stagedOrderDetails;
        private final Table<Pay> stagedPays;
        private final Table<PayDetail> stagedPayDetails;
        private final Table<User> stagedUsers;
        private final List<Long> pendingSms = new ArrayList<>();
        private final List<PaymentError> pendingErrors = new ArrayList<>();
        private final OrderDao orderDao;
        private final OrderDetailDao orderDetailDao;
        private final PayDao payDao;
        private final PayDetailDao payDetailDao;
        private final UserDao userDao;
        private boolean completed;

        private UnitOfWork() {
            stagedOrders = orders.copy(); stagedOrderDetails = orderDetails.copy();
            stagedPays = pays.copy(); stagedPayDetails = payDetails.copy(); stagedUsers = users.copy();
            orderDao = new OrderDao(new OrderMapper() {
                public Order selectById(Long id) { return stagedOrders.find(id); }
                public void insert(Order value) { stagedOrders.insert(value); }
                public void update(Order value) { stagedOrders.update(value); }
            });
            orderDetailDao = new OrderDetailDao(new OrderDetailMapper() {
                public OrderDetail selectById(Long id) { return stagedOrderDetails.find(id); }
                public void insert(OrderDetail value) { stagedOrderDetails.insert(value); }
                public void update(OrderDetail value) { stagedOrderDetails.update(value); }
            });
            payDao = new PayDao(new PayMapper() {
                public Pay selectById(Long id) { return stagedPays.find(id); }
                public void insert(Pay value) { stagedPays.insert(value); }
                public void update(Pay value) { stagedPays.update(value); }
            });
            payDetailDao = new PayDetailDao(new PayDetailMapper() {
                public PayDetail selectById(Long id) { return stagedPayDetails.find(id); }
                public void insert(PayDetail value) { stagedPayDetails.insert(value); }
                public void update(PayDetail value) { stagedPayDetails.update(value); }
            });
            userDao = new UserDao(new UserMapper() {
                public User selectById(Long id) { return stagedUsers.find(id); }
                public void insert(User value) { stagedUsers.insert(value); }
                public void update(User value) { stagedUsers.update(value); }
            });
        }

        public OrderDao orders() { return orderDao; }
        public OrderDetailDao orderDetails() { return orderDetailDao; }
        public PayDao pays() { return payDao; }
        public PayDetailDao payDetails() { return payDetailDao; }
        public UserDao users() { return userDao; }
        public void notifySms(Long id) { pendingSms.add(id); }
        public void recordError(Integer id, PayResultCode code) { pendingErrors.add(new PaymentError(id, code)); }

        public void commit(Long orderId, String directory) {
            if (completed) throw new IllegalStateException("UnitOfWork already completed");
            synchronized (LocalOrderStore.this) {
                orders = stagedOrders; orderDetails = stagedOrderDetails; pays = stagedPays;
                payDetails = stagedPayDetails; users = stagedUsers;
                directories.put(orderId, directory);
                smsNotifications.addAll(pendingSms); paymentErrors.addAll(pendingErrors);
            }
            completed = true;
        }
    }

    private static final class Table<T> {
        private final Map<Integer, T> rows = new LinkedHashMap<>();
        private final Function<T, Integer> id;
        private final Supplier<T> constructor;
        private Table(Function<T, Integer> id, Supplier<T> constructor) {
            this.id = id; this.constructor = constructor;
        }
        private Table<T> copy() {
            Table<T> duplicate = new Table<>(id, constructor);
            rows.forEach((key, value) -> duplicate.rows.put(key, copyEntity(value)));
            return duplicate;
        }
        private T find(Long key) {
            return key == null || key < 1 || key > Integer.MAX_VALUE ? null : copyEntity(rows.get(key.intValue()));
        }
        private void insert(T value) {
            Integer key = id.apply(value);
            if (key == null || rows.containsKey(key)) throw new IllegalStateException("Duplicate or absent entity ID: " + key);
            rows.put(key, copyEntity(value));
        }
        private void update(T value) {
            Integer key = id.apply(value);
            if (key == null || !rows.containsKey(key)) throw new IllegalStateException("Entity does not exist: " + key);
            rows.put(key, copyEntity(value));
        }
        private T copyEntity(T value) {
            if (value == null) return null;
            T copy = constructor.get();
            BeanUtils.copyProperties(value, copy);
            return copy;
        }
    }
}
