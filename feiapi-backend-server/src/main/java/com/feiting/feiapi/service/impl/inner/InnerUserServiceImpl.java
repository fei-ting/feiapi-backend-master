package com.feiting.feiapi.service.impl.inner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.UserMapper;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.service.InnerUserService;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

/**
 * @Author feiting
 */
@DubboService
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public User getInvokeUser(String accessKey) {
        if(StringUtils.isEmpty(accessKey)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("access_key", accessKey);

        return userMapper.selectOne(queryWrapper);
    }
}
