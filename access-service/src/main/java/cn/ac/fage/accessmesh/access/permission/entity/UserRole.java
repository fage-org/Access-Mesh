package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户角色关系实体
 * <p>
 * 表示用户与角色之间的关联关系。
 * 支持多种关联目标类型（角色、组织、岗位等）。
 * 支持时间有效期控制（validFrom/validTo）。
 * 一个用户可以通过多种方式关联到角色。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("user_role")
public class UserRole {

    /**
     * 用户角色关系唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 抽象用户ID
     */
    private Long abstractUserId;

    /**
     * 目标类型（ROLE=角色，ORG=组织，POSITION=岗位）
     */
    private String targetType;

    /**
     * 目标ID（根据targetType确定具体含义）
     */
    private Long targetId;

    /**
     * 关系ID（如角色ID、组织ID、岗位ID等）
     */
    private Long relationId;

    /**
     * 有效起始时间
     */
    private LocalDateTime validFrom;

    /**
     * 有效结束时间
     */
    private LocalDateTime validTo;

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