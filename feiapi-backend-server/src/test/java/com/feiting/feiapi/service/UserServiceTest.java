package com.feiting.feiapi.service;

import com.feiting.feiapicommon.model.entity.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;

/**
 * 用户服务测试
 *
 * @author yupi
 */
@SpringBootTest
class UserServiceTest {

    @Resource
    private UserService userService;

    @Test
    void testAddUser() {
        User user = new User();
        user.setUserAccount("testAddUser");
        user.setUserPassword("12345678");
        user.setAccessKey("test-add-ak");
        user.setSecretKey("test-add-sk");
        boolean result = userService.save(user);
        Assertions.assertTrue(result);
    }

    @Test
    void testUpdateUser() {
        // 先创建测试用户
        User user = new User();
        user.setUserAccount("testUpdateUser");
        user.setUserPassword("12345678");
        user.setAccessKey("test-update-ak");
        user.setSecretKey("test-update-sk");
        userService.save(user);
        // 更新昵称
        user.setUserName("测试昵称");
        boolean result = userService.updateById(user);
        Assertions.assertTrue(result);
    }

    @Test
    void testDeleteUser() {
        // 先创建测试用户
        User user = new User();
        user.setUserAccount("testDeleteUser");
        user.setUserPassword("12345678");
        user.setAccessKey("test-del-ak");
        user.setSecretKey("test-del-sk");
        userService.save(user);
        // 删除该用户
        boolean result = userService.removeById(user.getId());
        Assertions.assertTrue(result);
    }

    @Test
    void testGetUser() {
        // 先创建测试用户
        User user = new User();
        user.setUserAccount("testGetUser");
        user.setUserPassword("12345678");
        user.setAccessKey("test-get-ak");
        user.setSecretKey("test-get-sk");
        userService.save(user);
        // 根据 id 查询
        User result = userService.getById(user.getId());
        Assertions.assertNotNull(result);
        Assertions.assertEquals("testGetUser", result.getUserAccount());
    }

    @Test
    void userRegister() {
        String userAccount = "yupi";
        String userPassword = "";
        String checkPassword = "123456";
        try {
            long result = userService.userRegister(userAccount, userPassword, checkPassword);
            Assertions.assertEquals(-1, result);
            userAccount = "yu";
            result = userService.userRegister(userAccount, userPassword, checkPassword);
            Assertions.assertEquals(-1, result);
            userAccount = "yupi";
            userPassword = "123456";
            result = userService.userRegister(userAccount, userPassword, checkPassword);
            Assertions.assertEquals(-1, result);
            userAccount = "yu pi";
            userPassword = "12345678";
            result = userService.userRegister(userAccount, userPassword, checkPassword);
            Assertions.assertEquals(-1, result);
            checkPassword = "123456789";
            result = userService.userRegister(userAccount, userPassword, checkPassword);
            Assertions.assertEquals(-1, result);
            userAccount = "dogYupi";
            checkPassword = "12345678";
            result = userService.userRegister(userAccount, userPassword, checkPassword);
            Assertions.assertEquals(-1, result);
            userAccount = "yupi";
            result = userService.userRegister(userAccount, userPassword, checkPassword);
            Assertions.assertEquals(-1, result);
        } catch (Exception e) {

        }
    }
}
