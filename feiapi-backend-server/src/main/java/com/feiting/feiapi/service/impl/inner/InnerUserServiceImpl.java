package com.feiting.feiapi.service.impl.inner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.UserMapper;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.vo.InvokeUserVO;
import com.feiting.feiapicommon.service.InnerUserService;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;

import jakarta.annotation.Resource;

/**
 * 内部用户服务实现类
 */
@DubboService
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserMapper userMapper;

    /**
     * 根据 accessKey 查询网关调用所需的最小用户信息
     *
     * @param accessKey 调用方 accessKey
     * @return 内部调用用户视图，不存在时返回 null
     */
    @Override
    public InvokeUserVO getInvokeUser(String accessKey) {
        if (StringUtils.isEmpty(accessKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("access_key", accessKey);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            return null;
        }

        InvokeUserVO invokeUserVO = new InvokeUserVO();
        invokeUserVO.setId(user.getId());
        invokeUserVO.setAccessKey(user.getAccessKey());
        invokeUserVO.setSecretKey(user.getSecretKey());
        return invokeUserVO;
    }
}
