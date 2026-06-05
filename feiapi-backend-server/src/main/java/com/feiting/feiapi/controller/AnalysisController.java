package com.feiting.feiapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiting.feiapi.annotation.AuthCheck;
import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.common.ResultUtils;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分析控制器
 */
@RestController
@RequestMapping("/analysis")
@Slf4j
public class AnalysisController {

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @GetMapping("/top/interface/invoke")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<List<InterfaceInfoVO>> listTopInvokeInterfaceInfo() {
        //获取总调用次数前三的 UserInterfaceInfo 集合（已按 total_num desc 排序）
        List<UserInterfaceInfo> userInterfaceInfoList = userInterfaceInfoService.listTopInvokeInterfaceInfo(3);

        //无调用数据时返回空列表，而非抛异常
        if (CollectionUtils.isEmpty(userInterfaceInfoList)) {
            return ResultUtils.success(new ArrayList<>());
        }

        //使用 LinkedHashMap 保持 mapper 返回的排序顺序
        Map<Long, List<UserInterfaceInfo>> interfaceInfoIdObjMap = userInterfaceInfoList.stream()
                .collect(Collectors.groupingBy(
                        UserInterfaceInfo::getInterfaceInfoId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        //查询接口信息
        QueryWrapper<InterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", interfaceInfoIdObjMap.keySet());
        List<InterfaceInfo> list = interfaceInfoService.list(queryWrapper);

        //无匹配接口时返回空列表
        if (CollectionUtils.isEmpty(list)) {
            return ResultUtils.success(new ArrayList<>());
        }

        //将 InterfaceInfo 转为 Map，按 id 索引
        Map<Long, InterfaceInfo> interfaceInfoMap = list.stream()
                .collect(Collectors.toMap(InterfaceInfo::getId, info -> info));

        //按原始排序顺序（totalNum desc）遍历，保持排行榜顺序
        List<InterfaceInfoVO> interfaceInfoVOList = interfaceInfoIdObjMap.keySet().stream()
                .filter(interfaceInfoMap::containsKey)
                .map(interfaceInfoId -> {
                    InterfaceInfo interfaceInfo = interfaceInfoMap.get(interfaceInfoId);
                    InterfaceInfoVO interfaceInfoVO = new InterfaceInfoVO();
                    BeanUtils.copyProperties(interfaceInfo, interfaceInfoVO);
                    int totalNum = interfaceInfoIdObjMap.get(interfaceInfoId).get(0).getTotalNum();
                    interfaceInfoVO.setTotalNum(totalNum);
                    return interfaceInfoVO;
                })
                .collect(Collectors.toList());
        return ResultUtils.success(interfaceInfoVOList);
    }
}
