package org.springframework.jdbc.datasource.lookup.abstractds;

import java.util.List;

/**
 * Created by wangyingjie1 on 2016/2/21.
 */
public interface UserMapper {

    @DataSource("master")
    public void add(User user);

    @DataSource("master")
    public void update(User user);

    @DataSource("master")
    public void delete(int id);

    @DataSource("slave")
    public User loadbyid(int id);

    @DataSource("master")
    public User loadbyname(String name);

    @DataSource("slave")
    public List<User> list();


    // todo 模拟类
    class User{

    }
}
