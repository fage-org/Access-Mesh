package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 业务域配置实体
 * <p>
 * 表示业务域的配置信息，用于存储业务域特定的设置。
 * 配置类型包括：权限策略、审批流程、有效期规则等。
 * 扩展信息（extra）以JSON格式存储，支持灵活的配置扩展。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("domain_config")
public class DomainConfig {

    /**
     * 业务域配置唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 所属业务域ID
     */
    private Long bizDomainId;

    /**
     * 配置类型，标识配置的用途
     */
    private String configType;

    /**
     * 扩展信息（JSON格式），存储配置详情
     */
    private String extra;

    /**
     * 创建者用户ID
     */
    private Long createdBy;

    /**
     * 最后更新者用户ID
     */
    private Long updatedBy;

    /**
     * 删除者用户ID
     */
    private Long deletedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，其他=已删除）
     */
    private Long deleteFlag;
}