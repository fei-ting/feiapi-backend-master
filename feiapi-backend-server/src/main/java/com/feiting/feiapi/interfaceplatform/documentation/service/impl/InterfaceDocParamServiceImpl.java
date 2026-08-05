package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.interfaceplatform.documentation.mapper.InterfaceDocParamMapper;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam;
import com.feiting.feiapi.service.InterfaceDocParamService;
import org.springframework.stereotype.Service;

/**
 * 接口文档参数服务实现。
 */
@Service
public class InterfaceDocParamServiceImpl extends ServiceImpl<InterfaceDocParamMapper, InterfaceDocParam>
        implements InterfaceDocParamService {
}
