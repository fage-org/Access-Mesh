package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 统一组织表：部门/岗位/团队同表；默认组织树承担用户目录语义，非默认树只管理成员关系。
 * 组织/岗位同步为 ORG resource_entity（管理权限）和 ORG/POSITION abstract_role（角色容器），均使用业务键定位，不存 access-service 内部 ID。
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
