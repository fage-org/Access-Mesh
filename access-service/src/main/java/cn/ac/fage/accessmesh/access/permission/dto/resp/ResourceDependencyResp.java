package cn.ac.fage.accessmesh.access.permission.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 资源依赖关系响应体
 * <p>
 * 返回资源依赖关系的详细信息，包括源资源、目标资源、操作要求等。
 * 用于资源依赖查询接口的响应。
 * 操作位为 63 位 bigint 位值列，按仓库统一规则以字符串线格式下发
 * （T-PERM-028 binaryBit 同款，避免 JS Number 2^53 以上丢精度）；
 * 操作码数组不做反解（前端经操作列表建 bit 映射拆解，操作软删后残留位反解有歧义）。
 * </p>
 *
 * @param id                    依赖关系ID
 * @param tenantId              租户ID
 * @param resourceEntityId      源资源实体ID
 * @param sourceResourceCode    源资源编码
 * @param sourceResourceTypeCode 源资源类型编码（type_definition 反查）
 * @param sourceResourceName    源资源名称
 * @param dependsOnResourceEntityId 目标资源实体ID
 * @param depResourceCode       目标资源编码
 * @param targetResourceTypeCode 目标资源类型编码（type_definition 反查）
 * @param targetResourceName    目标资源名称
 * @param sourceOperationBits   源操作权限位（字符串线格式；null=任意操作触发）
 * @param requiredOperationBits 要求的操作权限位（字符串线格式）
 * @param autoGrant             是否自动授权（预留禁用，恒 false）
 * @param description           依赖关系描述
 * @param ownerServiceCode      维护方服务编码（UI 创建行为 null）
 * @param maintainSource        维护来源（ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC）
 * @param createdAt             创建时间
 * @param updatedAt             最后更新时间
 */
public record ResourceDependencyResp(
    Long id,
    Long tenantId,
    Long resourceEntityId,
    String sourceResourceCode,
    String sourceResourceTypeCode,
    String sourceResourceName,
    Long dependsOnResourceEntityId,
    String depResourceCode,
    String targetResourceTypeCode,
    String targetResourceName,
    @JsonSerialize(using = ToStringSerializer.class) Long sourceOperationBits,
    @JsonSerialize(using = ToStringSerializer.class) Long requiredOperationBits,
    Boolean autoGrant,
    String description,
    String ownerServiceCode,
    String maintainSource,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
