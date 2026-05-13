package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 权限版本实体
 * <p>
 * 表示角色权限的版本号记录。
 * 每次角色权限变更时版本号递增，用于缓存失效判断。
 * 通过版本号机制确保分布式环境下权限缓存的一致性。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("permission_version")
public class PermissionVersion {

    /**
     * 权限版本唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 抽象角色ID
     */
    private Long abstractRoleId;

    /**
     * 版本号，每次变更递增
     */
    private Long versionNo;

    /**
     * 触发实体类型（ROLE/RESOURCE/PERMISSION等）
     */
    private String triggerEntityType;

    /**
     * 触发实体ID
     */
    private Long triggerEntityId;

    /**
     * 备注，说明版本变更原因
     */
    private String remark;

    /**
     * 创建者用户ID
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}