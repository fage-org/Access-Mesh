package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 用户有效权限码聚合查询请求（v1.4 双轨并行 / 命名空间统一）。
 * <p>
 * 形态：
 * <ul>
 *   <li>本接口返回扁平 {@code Set<resourceTypeCode:operationCode>} 字符串集，
 *       供前端 hasPerms、功能开关、客户端能力下发等场景使用。<b>不分页、不可截断</b>，
 *       确保任何用户的所有有效权限码均被返回，消除 page=1, size=500 模式下大权限用户被截断的风险。</li>
 * </ul>
 * <p>
 * 调用方需要在 {@code resourceTypeCodes} 中显式声明白名单，避免下发无关资源类型。
 *
 * @param subjectTypeCode    主体类型码，如 "LOCAL_USER"
 * @param subjectExternalId  主体外部 ID，如 userId.toString()
 * @param resourceTypeCodes  资源类型码白名单（必填，非空）；仅这些类型的有效操作码会被聚合下发
 *
 * @see cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp
 */
public record UserEffectivePermissionCodesReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotEmpty List<String> resourceTypeCodes
) {}
