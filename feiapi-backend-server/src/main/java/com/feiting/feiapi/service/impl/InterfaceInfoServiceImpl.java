package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
* @author asus
* @description 针对表【interface_info(接口信息)】的数据库操作Service实现
* @createDate 2023-02-20 21:59:30
*/
@Service
public class InterfaceInfoServiceImpl extends ServiceImpl<InterfaceInfoMapper, InterfaceInfo>
    implements InterfaceInfoService {

    @Override
    public void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add) {
        // 空对象校验
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String name = interfaceInfo.getName();
        String url = interfaceInfo.getUrl();
        String method = interfaceInfo.getMethod();

        // 创建时，必填字段强制校验：name、url、method 不能为空
        if (add) {
            if (StringUtils.isAnyBlank(name, url, method)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口名称、URL、请求方法不能为空");
            }
        } else {
            // 更新时，只校验传入的核心字段不能是空白
            // name 如果传了，不能是空白
            if (name != null && name.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口名称不能为空白");
            }
            // url 如果传了，不能是空白
            if (url != null && url.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口URL不能为空白");
            }
            // method 如果传了，不能是空白
            if (method != null && method.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求方法不能为空白");
            }
        }

        // name 长度校验（创建和更新都适用）
        if (StringUtils.isNotBlank(name) && name.length() > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "名称过长");
        }
    }
}




