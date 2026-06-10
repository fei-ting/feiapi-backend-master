package com.feiting.feiapi.unit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.UserMapper;
import com.feiting.feiapi.service.impl.inner.InnerUserServiceImpl;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.vo.InvokeUserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 内部用户服务测试
 */
@DisplayName("InnerUserServiceImpl 单元测试")
class InnerUserServiceImplTest {

    /**
     * 校验内部用户服务只返回最小调用用户信息
     */
    @Test
    @DisplayName("根据 accessKey 返回最小调用用户信息")
    void shouldReturnMinimalInvokeUser() {
        UserMapper userMapper = mock(UserMapper.class);
        InnerUserServiceImpl service = new InnerUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        User user = new User();
        user.setId(1L);
        user.setUserAccount("invokeUser");
        user.setUserPassword("hashed-password");
        user.setUserRole("admin");
        user.setAccessKey("ak");
        user.setSecretKey("sk");
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);

        InvokeUserVO invokeUserVO = service.getInvokeUser("ak");

        assertThat(invokeUserVO.getId()).isEqualTo(1L);
        assertThat(invokeUserVO.getAccessKey()).isEqualTo("ak");
        assertThat(invokeUserVO.getSecretKey()).isEqualTo("sk");
    }

    /**
     * 校验 accessKey 为空时返回参数错误
     */
    @Test
    @DisplayName("accessKey 为空时抛出参数错误")
    void shouldThrowWhenAccessKeyBlank() {
        InnerUserServiceImpl service = new InnerUserServiceImpl();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getInvokeUser(""));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
    }

    /**
     * 校验内部调用用户视图只保留网关必需字段
     */
    @Test
    @DisplayName("内部调用用户视图只包含验签和计费必需字段")
    void shouldOnlyExposeRequiredFieldsForGateway() {
        Set<String> fieldNames = Arrays.stream(InvokeUserVO.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        assertThat(fieldNames)
                .containsExactlyInAnyOrder("id", "accessKey", "secretKey", "serialVersionUID");
    }
}
