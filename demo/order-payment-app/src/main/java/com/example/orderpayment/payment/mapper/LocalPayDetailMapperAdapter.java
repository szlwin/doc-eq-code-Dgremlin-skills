package com.example.orderpayment.payment.mapper;

import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import com.example.orderpayment.payment.entity.PayDetail;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Primary
public final class LocalPayDetailMapperAdapter implements PayDetailMapper {
    private final JdbcTemplate jdbc;
    LocalPayDetailMapperAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String SELECT = "SELECT pd_id,pd_pay_id,pd_product_id,pd_product_name,pd_pay_price,pd_pay_amount FROM dec_demo_pay_detail_info";
    private static final RowMapper<PayDetail> ROW = (r,n) -> { PayDetail v=new PayDetail(); v.setId(r.getInt("pd_id")); v.setPayId((Integer)r.getObject("pd_pay_id")); v.setProductId((Integer)r.getObject("pd_product_id")); v.setProductName(r.getString("pd_product_name")); v.setPayPrice(r.getBigDecimal("pd_pay_price")); v.setPayAmount(r.getBigDecimal("pd_pay_amount")); return v; };
    @Override public PayDetail selectById(Long id) {
        return jdbc.query(SELECT + " WHERE pd_id=?", ROW, id).stream().findFirst().orElse(null);
    }
    public List<PayDetail> selectByPayId(Long id) {
        return jdbc.query(SELECT + " WHERE pd_pay_id=? ORDER BY pd_id", ROW, id);
    }
    @Override public void insert(PayDetail v) { jdbc.update("INSERT INTO dec_demo_pay_detail_info VALUES (?,?,?,?,?,?)", v.getId(),v.getPayId(),v.getProductId(),v.getProductName(),v.getPayPrice(),v.getPayAmount()); }
    @Override public void update(PayDetail v) { jdbc.update("UPDATE dec_demo_pay_detail_info SET pd_pay_id=?,pd_product_id=?,pd_product_name=?,pd_pay_price=?,pd_pay_amount=? WHERE pd_id=?", v.getPayId(),v.getProductId(),v.getProductName(),v.getPayPrice(),v.getPayAmount(),v.getId()); }
}

