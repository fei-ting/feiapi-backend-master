package com.feiting.feiapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoQueryRequest;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.apache.ibatis.annotations.Param;

/**
* @author asus
* @description 针对表【interface_info(接口信息)】的数据库操作Mapper
* @createDate 2023-02-20 21:59:30
* @Entity generator.domain.InterfaceInfo
*/
public interface InterfaceInfoMapper extends BaseMapper<InterfaceInfo> {

    /**
     * 按接口调用总数分页查询接口视图。
     *
     * @param page         分页参数
     * @param queryRequest 查询条件
     * @param status       接口状态过滤值
     * @param method       标准化后的请求方法
     * @param asc          是否按调用总数升序排序
     * @return 接口视图分页结果
     */
    Page<InterfaceInfoVO> selectPageOrderByTotalNum(Page<InterfaceInfoVO> page,
                                                    @Param("queryRequest") InterfaceInfoQueryRequest queryRequest,
                                                    @Param("status") Integer status,
                                                    @Param("method") String method,
                                                    @Param("asc") boolean asc);
}




