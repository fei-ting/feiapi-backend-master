package com.feiting.feiapi.unit.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDoc;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocPublishSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.service.impl.InterfaceDocPublishReaderImpl;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceDocService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 接口文档发布快照读取服务单元测试。
 */
@DisplayName("接口文档发布快照读取服务单元测试")
class InterfaceDocPublishReaderImplTest {

    /**
     * 应读取文档主记录、参数和错误码并转换为只读快照。
     */
    @Test
    @DisplayName("读取并转换文档发布快照")
    @SuppressWarnings("unchecked")
    void shouldReadPublishSnapshot() {
        InterfaceDocService docService = mock(InterfaceDocService.class);
        InterfaceDocParamService paramService = mock(InterfaceDocParamService.class);
        InterfaceDocErrorCodeService errorCodeService = mock(InterfaceDocErrorCodeService.class);
        InterfaceDocPublishReaderImpl reader = new InterfaceDocPublishReaderImpl(
                docService, paramService, errorCodeService);

        LambdaQueryChainWrapper<InterfaceDoc> docQuery = mock(LambdaQueryChainWrapper.class);
        LambdaQueryChainWrapper<InterfaceDocParam> paramQuery = mock(LambdaQueryChainWrapper.class);
        LambdaQueryChainWrapper<InterfaceDocErrorCode> errorCodeQuery = mock(LambdaQueryChainWrapper.class);
        when(docService.lambdaQuery()).thenReturn(docQuery);
        when(paramService.lambdaQuery()).thenReturn(paramQuery);
        when(errorCodeService.lambdaQuery()).thenReturn(errorCodeQuery);
        when(docQuery.eq(any(), any())).thenReturn(docQuery);
        when(paramQuery.eq(any(), any())).thenReturn(paramQuery);
        when(paramQuery.orderByAsc((SFunction<InterfaceDocParam, ?>) any())).thenReturn(paramQuery);
        when(errorCodeQuery.eq(any(), any())).thenReturn(errorCodeQuery);
        when(errorCodeQuery.orderByAsc((SFunction<InterfaceDocErrorCode, ?>) any())).thenReturn(errorCodeQuery);
        when(docQuery.one()).thenReturn(buildDoc());
        when(paramQuery.list()).thenReturn(List.of(buildParam()));
        when(errorCodeQuery.list()).thenReturn(List.of(buildErrorCode()));

        InterfaceDocPublishSnapshot snapshot = reader.getPublishSnapshot(1L);

        assertThat(snapshot.getInterfaceInfoId()).isEqualTo(1L);
        assertThat(snapshot.getDocId()).isEqualTo(11L);
        assertThat(snapshot.getDocParams()).hasSize(1);
        assertThat(snapshot.getDocParams().get(0).getName()).isEqualTo("name");
        assertThat(snapshot.getErrorCodes()).hasSize(1);
        assertThat(snapshot.getErrorCodes().get(0).getErrorCode()).isEqualTo("A001");
    }

    /**
     * 构造文档主记录。
     *
     * @return 文档主记录
     */
    private InterfaceDoc buildDoc() {
        InterfaceDoc doc = new InterfaceDoc();
        doc.setId(11L);
        doc.setInterfaceInfoId(1L);
        doc.setDocStatus("READY");
        doc.setDocVersion("v1");
        doc.setRequestContentType("application/json");
        doc.setResponseContentType("application/json");
        return doc;
    }

    /**
     * 构造文档参数。
     *
     * @return 文档参数
     */
    private InterfaceDocParam buildParam() {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setId(21L);
        param.setInterfaceInfoId(1L);
        param.setName("name");
        param.setParamScene("BODY");
        param.setType("string");
        param.setRequired(1);
        param.setNullable(0);
        return param;
    }

    /**
     * 构造错误码。
     *
     * @return 错误码
     */
    private InterfaceDocErrorCode buildErrorCode() {
        InterfaceDocErrorCode errorCode = new InterfaceDocErrorCode();
        errorCode.setId(31L);
        errorCode.setInterfaceInfoId(1L);
        errorCode.setErrorCode("A001");
        errorCode.setErrorMessage("参数错误");
        return errorCode;
    }
}
