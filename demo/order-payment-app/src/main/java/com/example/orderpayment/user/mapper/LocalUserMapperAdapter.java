package com.example.orderpayment.user.mapper;

import com.example.orderpayment.user.entity.User;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Primary
final class LocalUserMapperAdapter implements UserMapper {
    private final JdbcTemplate jdbc;
    LocalUserMapperAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public User selectById(Long id) { return jdbc.query("SELECT u_id,u_name,u_active,u_certified FROM dec_demo_user_info WHERE u_id=?", (r,n) -> { User v=new User(); v.setId(r.getInt("u_id")); v.setName(r.getString("u_name")); v.setActive((Integer)r.getObject("u_active")); v.setCertified((Integer)r.getObject("u_certified")); return v; }, id).stream().findFirst().orElse(null); }
    @Override public void insert(User v) { jdbc.update("INSERT INTO dec_demo_user_info VALUES (?,?,?,?)", v.getId(),v.getName(),v.getActive(),v.getCertified()); }
    @Override public void update(User v) { jdbc.update("UPDATE dec_demo_user_info SET u_name=?,u_active=?,u_certified=? WHERE u_id=?", v.getName(),v.getActive(),v.getCertified(),v.getId()); }
}
