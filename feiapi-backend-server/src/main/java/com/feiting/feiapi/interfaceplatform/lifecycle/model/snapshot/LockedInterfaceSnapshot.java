package com.feiting.feiapi.interfaceplatform.lifecycle.model.snapshot;

import lombok.Builder;
import lombok.Value;

import java.util.Date;

/**
 * 已锁定接口只读快照。
 *
 * <p>该模型用于生命周期状态判断，不携带接口实体或 Mapper 写入能力。</p>
 */
@Value
@Builder
public class LockedInterfaceSnapshot {

    /**
     * 接口信息 ID。
     */
    Long interfaceInfoId;

    /**
     * 接口名称。
     */
    String name;

    /**
     * 接口状态。
     */
    Integer status;

    /**
     * 更新时间。
     */
    Date updateTime;
}
