package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 组织树配置实体类
 * <p>
 * 对应数据库表sys_org_tree_config，用于存储组织树配置。
 * 支持多种组织树类型、默认树设置、单关联配置等。
 * </p>
 */
@Getter
@Setter
@Table("sys_org_tree_config")
public class SysOrgTreeConfig {

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
     * 根组织ID
     */
    private Long rootOrgId;

    /**
     * 组织树名称
     */
    private String treeName;

    /**
     * 组织树类型
     */
    private String treeType;

    /**
     * 是否默认树
     */
    private Boolean isDefault;

    /**
     * 是否单关联（用户只能属于一个节点）
     */
    private Boolean singleAssoc;

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