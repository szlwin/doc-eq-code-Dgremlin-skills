package com.example.orderpayment.user.dao;

import org.springframework.stereotype.Repository;
import com.dec.lite.data.CrudDao;
import com.example.orderpayment.user.entity.User;
import com.example.orderpayment.user.mapper.UserMapper;

/** DEC DATA-USER; YAML data-order.yaml#datas[id=DATA-USER]; XML data-order.xml data[name=user]. */
@Repository
public class UserDao extends CrudDao<User> {
    public UserDao(UserMapper mapper) {
        super(mapper);
    }
}
