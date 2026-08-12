package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 业务域实体
 * <p>
 * 表示系统中的业务域划分，用于权限隔离和分类。
 * 业务域定义不同业务场景的边界，如：系统管理、业务运营、数据分析等。
 * 角色、资源等权限对象可绑定到特定业务域，实现权限的分域管理。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("biz_domain")
public class BizDomain {

    /**
     * 业务域唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 业务域编码，用于标识和引用
     */
    private String code;

    /**
     * 业务域名称
     */
    private String name;

    /**
     * 业务域描述，说明业务域用途和范围
     */
    private String description;

    /**
     * 是否全局域（每租户仅一个全局域，其范围隐式包含未被其他域认领的资源类型）
     */
    private Boolean global;

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