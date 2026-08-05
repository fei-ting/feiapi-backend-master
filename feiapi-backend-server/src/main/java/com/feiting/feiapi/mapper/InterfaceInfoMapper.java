package com.feiting.feiapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoQueryRequest;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
* @author asus
* @description 针对表【interface_info(接口信息)】的数据库操作Mapper
* @createDate 2023-02-20 21:59:30
* @Entity generator.domain.InterfaceInfo
*/
public interface InterfaceInfoMapper extends BaseMapper<InterfaceInfo> {

    /**
     * 按主键查询并锁定未删除的接口信息。
     *
     * @param id 接口信息 ID
     * @return 已锁定的接口信息，不存在时返回空
     */
    @Select("SELECT * FROM interface_info WHERE id = #{id} AND is_delete = 0 FOR UPDATE")
    InterfaceInfo selectByIdForUpdate(@Param("id") Long id);

    /**
     * 按接口 ID、下线状态和未删除条件逻辑删除接口主记录。
     *
     * @param id            接口信息 ID
     * @param offlineStatus 下线状态值
     * @return 影响行数
     */
    @Update("UPDATE interface_info SET is_delete = id WHERE id = #{id} AND status = #{offlineStatus} AND is_delete = 0")
    int logicDeleteOfflineById(@Param("id") Long id, @Param("offlineStatus") Integer offlineStatus);

    /**
     * 按接口 ID 查询配额类型，不过滤逻辑删除记录。
     *
     * @param id 接口信息 ID
     * @return 接口配额类型，不存在时返回空
     */
    @Select("SELECT quota_type FROM interface_info WHERE id = #{id}")
    String selectQuotaTypeIncludeDeleted(@Param("id") Long id);

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




