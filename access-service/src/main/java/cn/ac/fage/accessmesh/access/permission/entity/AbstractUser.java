package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import cn.ac.fage.accessmesh.access.infrastructure.JsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 抽象用户实体
 * <p>
 * 表示系统中的用户主体，支持多种用户类型。
 * 用户类型包括：系统用户、组织用户、外部用户等。
 * 采用抽象设计，不直接存储认证信息，认证信息由外部服务管理。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("abstract_user")
public class AbstractUser {

    /**
     * 用户唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 用户类型（0=系统用户，1=组织用户，2=外部用户）
     */
    private Integer userType;

    /**
     * 外部标识，用于关联外部系统用户
     */
    private String externalId;

    /**
     * 用户名称
     */
    private String name;

    /**
     * 启用状态（true=启用，false=禁用）
     */
    private Boolean enabled;

    /**
     * 扩展信息（JSON格式），存储额外属性
     */
    @Column(typeHandler = JsonbStringTypeHandler.class)
    private String extra;

    /**
     * 所有权标识（可空，T-ACCESS-002）：
     * access-service=管理事实派生的本地投影（禁止权限管理 API 直接修改）；
     * NULL=人工维护或外部同步（外部同步所有权以 sync_metadata 为准）
     */
    private String ownerServiceCode;

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