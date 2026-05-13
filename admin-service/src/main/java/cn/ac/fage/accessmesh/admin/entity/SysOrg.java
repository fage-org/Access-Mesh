package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统组织实体类
 * <p>
 * 对应数据库表sys_org，用于存储组织机构信息。
 * 支持树形组织结构、组织类型、组织编码、权限关联等。
 * </p>
 */
@Getter
@Setter
@Table("sys_org")
public class SysOrg {

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
     * 父组织ID
     */
    private Long parentId;

    /**
     * 组织类型
     */
    private String orgType;

    /**
     * 组织编码
     */
    private String code;

    /**
     * 组织名称
     */
    private String name;

    /**
     * 组织路径（祖先ID链）
     */
    private String path;

    /**
     * 组织层级（深度）
     */
    private Integer level;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 组织负责人ID
     */
    private Long leaderId;

    /**
     * 状态（0=正常，1=禁用）
     */
    private Integer status;

    /**
     * 权限角色ID（组织对应角色）
     */
    private Long permRoleId;

    /**
     * 权限组织ID（对应权限中心的组织）
     */
    private Long permOrgId;

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