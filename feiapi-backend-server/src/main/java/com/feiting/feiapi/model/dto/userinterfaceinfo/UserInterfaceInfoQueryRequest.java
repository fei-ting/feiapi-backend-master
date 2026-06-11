package com.feiting.feiapi.model.dto.userinterfaceinfo;

import com.feiting.feiapi.common.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询请求
 *
 * @author yupi
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserInterfaceInfoQueryRequest extends PageRequest implements Serializable {

    /**
     * 主键
     */
    @Positive(message = "主键必须大于 0")
    private Long id;

    /**
     * 调用人 id
     */
    @Positive(message = "调用人 id 必须大于 0")
    private Long userId;

    /**
     * 接口 id
     */
    @Positive(message = "接口 id 必须大于 0")
    private Long interfaceInfoId;

    /**
     * 总调用次数
     */
    @Min(value = 0, message = "总调用次数不能小于 0")
    private Integer totalNum;

    /**
     * 剩余调用次数
     */
    @Min(value = 0, message = "剩余调用次数不能小于 0")
    private Integer leftNum;

    /**
     * 0-正常，1-禁用
     */
    @Min(value = 0, message = "状态不能小于 0")
    @Max(value = 1, message = "状态不能大于 1")
    private Integer status;


    private static final long serialVersionUID = 1L;
}
