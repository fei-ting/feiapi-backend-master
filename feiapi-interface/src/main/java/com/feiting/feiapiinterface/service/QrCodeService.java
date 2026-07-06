package com.feiting.feiapiinterface.service;

import com.feiting.feiapiinterface.model.dto.QrCodeGenerateRequest;
import com.feiting.feiapiinterface.model.vo.QrCodeGenerateVO;

/**
 * 二维码生成服务接口
 *
 * @author feiting
 */
public interface QrCodeService {

    /**
     * 生成二维码
     *
     * @param request 二维码生成请求参数
     * @return 二维码生成结果（包含 Base64 和 Data URI）
     */
    QrCodeGenerateVO generateQrCode(QrCodeGenerateRequest request);
}
