package com.example.orderpayment.order.mapper;

import com.example.orderpayment.order.entity.Order;
import com.example.orderpayment.order.enums.OrderStatus;
import com.example.orderpayment.order.enums.OrderType;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;

@Component
@Primary
final class LocalOrderMapperAdapter implements OrderMapper {
    private final JdbcTemplate jdbc;
    LocalOrderMapperAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Order selectById(Long id) {
        return jdbc.query("SELECT o_id,o_user_id,o_product_count,o_total_price,o_total_amount,o_status,o_pay_id,o_date,o_remark,o_type "
                        + "FROM dec_demo_order_info WHERE o_id = ?", (row, n) -> {
            Order value = new Order(); value.setId(row.getInt("o_id")); value.setUserId((Integer) row.getObject("o_user_id"));
            value.setProductCount((Integer) row.getObject("o_product_count")); value.setTotalPrice(row.getBigDecimal("o_total_price"));
            value.setTotalAmount(row.getBigDecimal("o_total_amount")); value.setOrderStatus(OrderStatus.fromValue((Integer) row.getObject("o_status")));
            value.setPayId((Integer) row.getObject("o_pay_id")); Date date = row.getDate("o_date"); value.setCreateDate(date == null ? null : date.toLocalDate());
            value.setRemark(row.getString("o_remark")); value.setOrderType(OrderType.fromValue((Integer) row.getObject("o_type"))); return value;
        }, id).stream().findFirst().orElse(null);
    }
    @Override public void insert(Order v) { jdbc.update("INSERT INTO dec_demo_order_info VALUES (?,?,?,?,?,?,?,?,?,?)", v.getId(), v.getUserId(), v.getProductCount(), v.getTotalPrice(), v.getTotalAmount(), value(v.getOrderStatus()), v.getPayId(), v.getCreateDate(), v.getRemark(), value(v.getOrderType())); }
    @Override public void update(Order v) { jdbc.update("UPDATE dec_demo_order_info SET o_user_id=?,o_product_count=?,o_total_price=?,o_total_amount=?,o_status=?,o_pay_id=?,o_date=?,o_remark=?,o_type=? WHERE o_id=?", v.getUserId(), v.getProductCount(), v.getTotalPrice(), v.getTotalAmount(), value(v.getOrderStatus()), v.getPayId(), v.getCreateDate(), v.getRemark(), value(v.getOrderType()), v.getId()); }
    private static Integer value(OrderStatus v) { return v == null ? null : v.getValue(); }
    private static Integer value(OrderType v) { return v == null ? null : v.getValue(); }
}
