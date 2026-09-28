package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 操作准入在线判定请求体（T-ACCESS-059，契约总册 §25.2）。
 * <p>
 * 网关本地判定回源／灰度强制在线用；沿 check-interface 形态。
 * 租户与调用方服务身份取自可信认证链（M2M 凭证行派生 / 内部密钥头），不接受请求体自报；
 * 被检查主体由可信调用方（Gateway／SDK 服务身份）在请求体断言，服务端在该租户范围内解析。
 * </p>
 *
 * @param subjectTypeCode   主体类型编码，必填
 * @param subjectExternalId 主体外部标识，必填
 * @param serviceCode       服务编码，必填
 * @param httpMethod        HTTP 方法，必填
 * @param path              请求路径，必填
 * @param context           评估上下文，可选（仅承载 clientIp；顶层保留键由服务端结构拒绝）
 */
public record InterfaceAdmissionReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String serviceCode,
    @NotBlank String httpMethod,
    @NotBlank String path,
    Map<String, Object> context
) {}
