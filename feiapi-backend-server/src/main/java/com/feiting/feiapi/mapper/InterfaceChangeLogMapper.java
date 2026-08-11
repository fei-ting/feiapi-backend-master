package com.feiting.feiapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feiting.feiapicommon.model.entity.InterfaceChangeLog;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 接口变更审计日志 Mapper。
 */
public interface InterfaceChangeLogMapper extends BaseMapper<InterfaceChangeLog> {

    /**
     * 查询最近的接口变更记录。
     *
     * @param limit 最大记录数
     * @return 最近变更记录
     */
    @Select("SELECT id, interface_info_id, interface_name, change_type, event_time, create_time "
            + "FROM interface_change_log ORDER BY event_time DESC, id DESC LIMIT #{limit}")
    List<InterfaceChangeLog> selectRecent(int limit);
}
