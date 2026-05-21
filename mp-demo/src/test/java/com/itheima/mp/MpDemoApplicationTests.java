package com.itheima.mp;

import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.itheima.mp.entity.User;
import com.itheima.mp.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class MpDemoApplicationTests {
    @Autowired
    public UserMapper userMapper;
    @Test
    void testQueryUserByIds() {
        List<User> users = userMapper.selectBatchIds(List.of(1, 2, 3));
        List<Long> usersId = users.stream().map(User::getId).collect(Collectors.toList());
        List<User> userList = Db.lambdaQuery(User.class).in(User::getId, usersId)
                .list();
        System.out.println(userList);
    }

}
