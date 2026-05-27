package com.feiting.feiapi.service;

import com.feiting.feiapi.mapper.UserInterfaceInfoMapper;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;


@SpringBootTest
public class UserInterfaceInfoServiceTest {

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private UserService userService;

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Resource
    private UserInterfaceInfoMapper userInterfaceInfoMapper;

    @Test
    void testInvokeCount() {
        // 创建测试用户
        User user = new User();
        user.setUserAccount("testInvokeUser");
        user.setUserPassword("12345678");
        user.setAccessKey("test-ak");
        user.setSecretKey("test-sk");
        userService.save(user);

        // 创建测试接口信息
        InterfaceInfo info = new InterfaceInfo();
        info.setName("testInvokeApi");
        info.setDescription("CI 测试接口");
        info.setUrl("http://localhost:8123/api/test");
        info.setMethod("GET");
        info.setUserId(user.getId());
        info.setStatus(1);
        interfaceInfoService.save(info);

        // 初始化用户-接口调用关系（剩余 100 次）
        UserInterfaceInfo record = new UserInterfaceInfo();
        record.setUserId(user.getId());
        record.setInterfaceInfoId(info.getId());
        record.setLeftNum(100);
        record.setTotalNum(0);
        userInterfaceInfoMapper.insert(record);

        // 执行一次调用
        boolean result = userInterfaceInfoService.invokeCount(user.getId(), info.getId());
        Assertions.assertTrue(result);

        // 验证调用次数已扣减
        UserInterfaceInfo updated = userInterfaceInfoMapper.selectById(record.getId());
        Assertions.assertEquals(99, updated.getLeftNum());
        Assertions.assertEquals(1, updated.getTotalNum());
    }
}
