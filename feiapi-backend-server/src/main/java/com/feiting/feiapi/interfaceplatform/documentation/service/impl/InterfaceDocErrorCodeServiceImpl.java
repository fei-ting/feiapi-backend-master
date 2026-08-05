package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.interfaceplatform.documentation.mapper.InterfaceDocErrorCodeMapper;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import org.springframework.stereotype.Service;

/**
 * 接口文档错误码服务实现。
 */
@Service
public class InterfaceDocErrorCodeServiceImpl extends ServiceImpl<InterfaceDocErrorCodeMapper, InterfaceDocErrorCode>
        implements InterfaceDocErrorCodeService {
}
