package com.feiting.feiapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feiting.feiapicommon.model.entity.User;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper
 *
 * @Entity com.feiting.feiapicommon.model.entity.User
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查询并锁定所有未删除管理员用户 id
     *
     * @return 管理员用户 id 列表
     */
    @Select("SELECT id FROM user WHERE user_role = 'admin' AND is_delete = 0 FOR UPDATE")
    List<Long> selectAdminIdsForUpdate();

}



