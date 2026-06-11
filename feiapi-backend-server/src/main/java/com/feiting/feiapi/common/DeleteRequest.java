package com.feiting.feiapi.common;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;

/**
 * 删除请求
 *
 * @author yupi
 */
@Data
public class DeleteRequest implements Serializable {
    /**
     * id
     */
    @NotNull(message = "id 不能为空")
    @Positive(message = "id 必须大于 0")
    private Long id;

    private static final long serialVersionUID = 1L;
}
