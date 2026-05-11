package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

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

    /**
     * 获取业务域唯一标识
     *
     * @return 业务域ID
     */
    public Long getId() { return id; }

    /**
     * 设置业务域唯一标识
     *
     * @param id 业务域ID
     */
    public void setId(Long id) { this.id = id; }

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    public Long getTenantId() { return tenantId; }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     */
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    /**
     * 获取业务域编码
     *
     * @return 业务域编码
     */
    public String getCode() { return code; }

    /**
     * 设置业务域编码
     *
     * @param code 业务域编码
     */
    public void setCode(String code) { this.code = code; }

    /**
     * 获取业务域名称
     *
     * @return 业务域名称
     */
    public String getName() { return name; }

    /**
     * 设置业务域名称
     *
     * @param name 业务域名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取业务域描述
     *
     * @return 业务域描述
     */
    public String getDescription() { return description; }

    /**
     * 设置业务域描述
     *
     * @param description 业务域描述
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取是否全局域
     *
     * @return 是否全局域
     */
    public Boolean getGlobal() { return global; }

    /**
     * 设置是否全局域
     *
     * @param global 是否全局域
     */
    public void setGlobal(Boolean global) { this.global = global; }

    /**
     * 获取创建者用户ID
     *
     * @return 创建者用户ID
     */
    public Long getCreatedBy() { return createdBy; }

    /**
     * 设置创建者用户ID
     *
     * @param createdBy 创建者用户ID
     */
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    /**
     * 获取最后更新者用户ID
     *
     * @return 最后更新者用户ID
     */
    public Long getUpdatedBy() { return updatedBy; }

    /**
     * 设置最后更新者用户ID
     *
     * @param updatedBy 最后更新者用户ID
     */
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    /**
     * 获取删除者用户ID
     *
     * @return 删除者用户ID
     */
    public Long getDeletedBy() { return deletedBy; }

    /**
     * 设置删除者用户ID
     *
     * @param deletedBy 删除者用户ID
     */
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * 设置创建时间
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * 获取最后更新时间
     *
     * @return 最后更新时间
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * 设置最后更新时间
     *
     * @param updatedAt 最后更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 获取删除时间
     *
     * @return 删除时间
     */
    public LocalDateTime getDeletedAt() { return deletedAt; }

    /**
     * 设置删除时间
     *
     * @param deletedAt 删除时间
     */
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    /**
     * 获取删除标记
     *
     * @return 删除标记（0=未删除，其他=已删除）
     */
    public Long getDeleteFlag() { return deleteFlag; }

    /**
     * 设置删除标记
     *
     * @param deleteFlag 删除标记
     */
    public void setDeleteFlag(Long deleteFlag) { this.deleteFlag = deleteFlag; }
}