package com.feiting.feiapi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoQueryRequest;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
* @author asus
* @description 针对表【interface_info(接口信息)】的数据库操作Service
* @createDate 2023-02-20 21:59:30
*/
public interface InterfaceInfoService extends IService<InterfaceInfo> {

    /**
     * 校验
     *
     * @param interfaceInfo
     * @param add     是否为创建校验
     */
    void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add);

    /**
     * 按接口调用总数分页查询接口视图。
     *
     * @param queryRequest 查询条件
     * @param status       接口状态过滤值
     * @param asc          是否按调用总数升序排序
     * @return 接口视图分页结果
     */
    Page<InterfaceInfoVO> listPageOrderByTotalNum(InterfaceInfoQueryRequest queryRequest, Integer status, boolean asc);
}
