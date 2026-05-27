package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.mapper.UserInterfaceInfoMapper;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
* @author asus
* @description 针对表【user_interface_info(用户调用接口关系)】的数据库操作Service实现
* @createDate 2023-03-02 22:31:51
*/
@Service
public class UserInterfaceInfoServiceImpl extends ServiceImpl<UserInterfaceInfoMapper, UserInterfaceInfo>
    implements UserInterfaceInfoService{

    @Resource
    private UserInterfaceInfoMapper userInterfaceInfoMapper;

    @Override
    public void validUserInterfaceInfo(UserInterfaceInfo userInterfaceInfo, boolean add) {
        if (userInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 创建时，所有参数必须非空
        if (add) {
            if (userInterfaceInfo.getUserId() <= 0 || userInterfaceInfo.getInterfaceInfoId() <= 0){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户或接口不存在");
            }
        }
        if(userInterfaceInfo.getLeftNum() < 0){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"剩余调用次数不能小于0");
        }
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public boolean invokeCount(long userId, long interfaceInfoId) {
        if(userId <= 0 || interfaceInfoId <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"接口或用户不存在");
        }

        UpdateWrapper<UserInterfaceInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("userId", userId);
        updateWrapper.eq("interfaceInfoId", interfaceInfoId);
        updateWrapper.gt("leftNum", 0);
        //sql拼接
        updateWrapper.setSql("leftNum = leftNum - 1, totalNum = totalNum + 1");

        return this.update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public boolean leftNumIsEnough(long userId, long interfaceInfoId){
        if(userId <= 0 || interfaceInfoId <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"接口或用户不存在");
        }

        QueryWrapper<UserInterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.eq("interfaceInfoId", interfaceInfoId);
        UserInterfaceInfo userInterfaceInfo = userInterfaceInfoMapper.selectOne(queryWrapper);

        if(userInterfaceInfo != null && userInterfaceInfo.getLeftNum() <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"接口调用次数不足，请充值");
        }

        if(userInterfaceInfo == null){
            UserInterfaceInfo addUserInterfaceInfo = new UserInterfaceInfo();
            addUserInterfaceInfo.setUserId(userId);
            addUserInterfaceInfo.setInterfaceInfoId(interfaceInfoId);
            addUserInterfaceInfo.setLeftNum(100);
            userInterfaceInfoMapper.insert(addUserInterfaceInfo);
        }

        return true;
    }

    @Override
    public List<UserInterfaceInfo> listTopInvokeInterfaceInfo(int limit){
        return userInterfaceInfoMapper.listTopInvokeInterfaceInfo(limit);
    }
}




