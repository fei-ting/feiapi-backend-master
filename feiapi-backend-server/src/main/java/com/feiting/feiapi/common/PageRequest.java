package com.feiting.feiapi.common;

import com.feiting.feiapi.constant.CommonConstant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页请求
 *
 * @author yupi
 */
@Data
public class PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页号
     */
    @Positive(message = "当前页号必须大于 0")
    private long current = 1;

    /**
     * 页面大小
     */
    @Positive(message = "页面大小必须大于 0")
    @Max(value = 50, message = "页面大小不能超过 50")
    private long pageSize = 10;

    /**
     * 排序字段
     */
    @Size(max = 64, message = "排序字段长度不能超过 64")
    private String sortField;

    /**
     * 排序顺序（默认升序）
     */
    @Pattern(regexp = "^(ascend|descend)?$", message = "排序顺序只能是 ascend 或 descend")
    private String sortOrder = CommonConstant.SORT_ORDER_ASC;
}
