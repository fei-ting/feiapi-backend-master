package com.feiting.feiapi.unit.dto;

import com.feiting.feiapi.common.DeleteRequest;
import com.feiting.feiapi.common.IdRequest;
import com.feiting.feiapi.common.PageRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoInvokeRequest;
import com.feiting.feiapi.model.dto.user.UserRegisterRequest;
import com.feiting.feiapi.model.dto.user.UserUpdateRequest;
import com.feiting.feiapi.model.dto.userinterfaceinfo.UserInterfaceInfoQueryRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO 参数校验单元测试
 */
@DisplayName("DTO Bean Validation 单元测试")
class DtoValidationTest {

    /**
     * 校验器工厂
     */
    private static ValidatorFactory validatorFactory;

    /**
     * Bean Validation 校验器
     */
    private static Validator validator;

    /**
     * 初始化 Bean Validation 校验器
     */
    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    /**
     * 释放 Bean Validation 校验器资源
     */
    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    /**
     * 注册请求应校验账号和密码的必填与长度
     */
    @Test
    @DisplayName("注册请求校验账号和密码边界")
    void shouldValidateUserRegisterRequiredFields() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUserAccount("abc");
        request.setUserPassword("1234567");
        request.setCheckPassword("");

        assertThat(violationMessages(request))
                .contains("账号长度必须在 4 到 256 之间",
                        "密码长度必须在 8 到 512 之间",
                        "确认密码不能为空");
    }

    /**
     * 接口创建请求应校验必填字段与字段长度
     */
    @Test
    @DisplayName("接口创建请求校验必填字段和长度")
    void shouldValidateInterfaceAddRequest() {
        InterfaceInfoAddRequest request = new InterfaceInfoAddRequest();
        request.setName(" ");
        request.setUrl("");
        request.setMethod("POST");
        request.setDescription("a".repeat(513));

        assertThat(violationMessages(request))
                .contains("接口名称不能为空",
                        "接口地址不能为空",
                        "接口描述长度不能超过 512");
    }

    /**
     * 用户更新请求应兼容空白密码，但拒绝过短的新密码
     */
    @Test
    @DisplayName("用户更新请求允许空白密码但拒绝过短密码")
    void shouldAllowBlankUpdatePasswordAndRejectShortPassword() {
        UserUpdateRequest blankPasswordRequest = new UserUpdateRequest();
        blankPasswordRequest.setId(1L);
        blankPasswordRequest.setUserPassword("   ");

        assertThat(violationMessages(blankPasswordRequest)).isEmpty();

        UserUpdateRequest shortPasswordRequest = new UserUpdateRequest();
        shortPasswordRequest.setId(1L);
        shortPasswordRequest.setUserPassword("short");

        assertThat(violationMessages(shortPasswordRequest))
                .contains("密码为空或长度至少 8");
    }

    /**
     * 分页请求应校验页码、页大小和排序方向
     */
    @Test
    @DisplayName("分页请求校验页码页大小和排序方向")
    void shouldValidatePageRequestBoundary() {
        PageRequest request = new PageRequest();
        request.setCurrent(0);
        request.setPageSize(51);
        request.setSortOrder("invalid");

        assertThat(violationMessages(request))
                .contains("当前页号必须大于 0",
                        "页面大小不能超过 50",
                        "排序顺序只能是 ascend 或 descend");
    }

    /**
     * 通用 id 请求应校验 id 不能为空且必须为正数
     */
    @Test
    @DisplayName("通用 id 请求校验正数 id")
    void shouldValidateCommonIdRequests() {
        DeleteRequest deleteRequest = new DeleteRequest();
        IdRequest idRequest = new IdRequest();
        idRequest.setId(0L);

        assertThat(violationMessages(deleteRequest)).contains("id 不能为空");
        assertThat(violationMessages(idRequest)).contains("id 必须大于 0");
    }

    /**
     * 接口调用请求应校验接口 id
     */
    @Test
    @DisplayName("接口调用请求校验接口 id")
    void shouldValidateInterfaceInvokeRequestId() {
        InterfaceInfoInvokeRequest request = new InterfaceInfoInvokeRequest();
        request.setId(-1L);

        assertThat(violationMessages(request)).contains("接口 id 必须大于 0");
    }

    /**
     * 调用关系查询请求应校验 id、次数和状态边界
     */
    @Test
    @DisplayName("调用关系查询请求校验数字边界")
    void shouldValidateUserInterfaceInfoQueryBoundary() {
        UserInterfaceInfoQueryRequest request = new UserInterfaceInfoQueryRequest();
        request.setId(0L);
        request.setTotalNum(-1);
        request.setLeftNum(-1);
        request.setStatus(2);

        assertThat(violationMessages(request))
                .contains("主键必须大于 0",
                        "总调用次数不能小于 0",
                        "剩余调用次数不能小于 0",
                        "状态不能大于 1");
    }

    /**
     * 获取对象的 Bean Validation 错误信息
     *
     * @param target 待校验对象
     * @return 校验错误信息集合
     */
    private Set<String> violationMessages(Object target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
