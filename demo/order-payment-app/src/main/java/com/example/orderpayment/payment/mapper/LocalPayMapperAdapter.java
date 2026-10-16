package com.example.orderpayment.payment.mapper;

import com.example.orderpayment.payment.entity.Pay;
import com.example.orderpayment.payment.enums.PayResultCode;
import com.example.orderpayment.payment.enums.PaymentStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Primary
final class LocalPayMapperAdapter implements PayMapper {
    private final JdbcTemplate jdbc;
    LocalPayMapperAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Pay selectById(Long id) { return jdbc.query("SELECT p_id,p_order_id,p_user_id,p_status,p_result_code FROM dec_demo_pay_info WHERE p_id=?", (r,n) -> { Pay v=new Pay(); v.setId(r.getInt("p_id")); v.setOrderId((Integer)r.getObject("p_order_id")); v.setUserId((Integer)r.getObject("p_user_id")); v.setStatus(PaymentStatus.fromValue((Integer)r.getObject("p_status"))); Integer code=(Integer)r.getObject("p_result_code"); v.setResultCode(code == null ? null : PayResultCode.fromValue(code)); return v; }, id).stream().findFirst().orElse(null); }
    @Override public void insert(Pay v) { jdbc.update("INSERT INTO dec_demo_pay_info VALUES (?,?,?,?,?)", v.getId(),v.getOrderId(),v.getUserId(),v.getStatus().getValue(),v.getResultCode()==null?null:v.getResultCode().getValue()); }
    @Override public void update(Pay v) { jdbc.update("UPDATE dec_demo_pay_info SET p_order_id=?,p_user_id=?,p_status=?,p_result_code=? WHERE p_id=?", v.getOrderId(),v.getUserId(),v.getStatus().getValue(),v.getResultCode()==null?null:v.getResultCode().getValue(),v.getId()); }
}

