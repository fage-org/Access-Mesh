package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 域配置保存请求体
 * <p>
 * 用于保存或更新域配置，包括业务域编码、配置类型和配置值。
 * </p>
 *
 * @param domainCode 业务域编码，必填
 * @param configType 配置类型编码，必填；仅接受已实现类型（与 {@code ConfigType} 枚举一致：SUB_PERM/CLASSIFY，
 *                   历史 SCOPE/RELATION/BINDING 未实现，请求拒绝）
 * @param extra      配置值JSON，必填
 */
public record DomainConfigReq(
    @NotBlank String domainCode,
    @NotBlank @Pattern(regexp = "SUB_PERM|CLASSIFY",
        message = "configType 仅支持 SUB_PERM/CLASSIFY（SCOPE/RELATION/BINDING 未实现）") String configType,
    @NotBlank String extra
) {}
