package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户组织关联实体类
 * <p>
 * 对应数据库表sys_user_org，用于存储用户和组织的关联关系。
 * 支持用户属于多个组织，可标记主要组织。
 * 用户与组织节点关系（默认树/非默认树语义见 @see 第 2 节，同步契约见第 5.3 节）。
 * @see docs/design/default-org-tree-user-lifecycle.md
 * </p>
 */
@Getter
@Setter
@Table("sys_user_org")
public class SysUserOrg {

    /**
     * 主键ID（自增）
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 是否主要组织（首期限默认树，见类 @see 第 6 节）
     */
    private Boolean isPrimary;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 删除人ID
     */
    private Long deletedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，1=已删除）
     */
    private Long deleteFlag;
}
