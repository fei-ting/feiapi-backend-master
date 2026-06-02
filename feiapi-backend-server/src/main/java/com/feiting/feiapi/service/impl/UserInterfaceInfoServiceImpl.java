package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.UserInterfaceInfoMapper;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 用户接口调用关系服务实现
 */
@Service
public class UserInterfaceInfoServiceImpl extends ServiceImpl<UserInterfaceInfoMapper, UserInterfaceInfo>
        implements UserInterfaceInfoService {

    @Resource
    private UserInterfaceInfoMapper userInterfaceInfoMapper;

    @Override
    public void validUserInterfaceInfo(UserInterfaceInfo userInterfaceInfo, boolean add) {
        if (userInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (add && (userInterfaceInfo.getUserId() <= 0 || userInterfaceInfo.getInterfaceInfoId() <= 0)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户或接口不存在");
        }
        if (userInterfaceInfo.getLeftNum() != null && userInterfaceInfo.getLeftNum() < 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "剩余调用次数不能小于0");
        }
    }

    @Override
    public boolean invokeCount(long userId, long interfaceInfoId) {
        if (userId <= 0 || interfaceInfoId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口或用户不存在");
        }

        UpdateWrapper<UserInterfaceInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("user_id", userId);
        updateWrapper.eq("interface_info_id", interfaceInfoId);
        updateWrapper.eq("is_delete", 0);
        updateWrapper.gt("left_num", 0);
        updateWrapper.setSql("left_num = left_num - 1, total_num = total_num + 1");
        return this.update(updateWrapper);
    }

    @Override
    public boolean leftNumIsEnough(long userId, long interfaceInfoId) {
        if (userId <= 0 || interfaceInfoId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口或用户不存在");
        }

        UserInterfaceInfo userInterfaceInfo = getUserInterfaceInfo(userId, interfaceInfoId);
        if (userInterfaceInfo == null) {
            userInterfaceInfoMapper.insertIgnoreIfAbsent(userId, interfaceInfoId, 100, 0);
            userInterfaceInfo = getUserInterfaceInfo(userId, interfaceInfoId);
            if (userInterfaceInfo == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化接口调用关系失败");
            }
        }

        if (userInterfaceInfo.getLeftNum() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口调用次数不足，请充值");
        }

        return true;
    }

    @Override
    public List<UserInterfaceInfo> listTopInvokeInterfaceInfo(int limit) {
        return userInterfaceInfoMapper.listTopInvokeInterfaceInfo(limit);
    }

    private UserInterfaceInfo getUserInterfaceInfo(long userId, long interfaceInfoId) {
        QueryWrapper<UserInterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("interface_info_id", interfaceInfoId);
        queryWrapper.eq("is_delete", 0);
        return userInterfaceInfoMapper.selectOne(queryWrapper);
    }
}
