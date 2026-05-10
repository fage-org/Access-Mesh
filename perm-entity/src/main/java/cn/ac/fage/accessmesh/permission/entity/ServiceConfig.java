package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 服务配置实体
 * <p>
 * 表示微服务的配置信息。
 * 定义服务的编码、名称、基础路径等信息。
 * 用于资源归属服务和API权限校验的服务识别。
 * 支持多租户环境下不同租户的服务配置隔离。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("service_config")
public class ServiceConfig {

    /**
     * 服务配置唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 服务编码，用于标识和引用服务
     */
    private String serviceCode;

    /**
     * 服务名称
     */
    private String name;

    /**
     * 基础路径，API请求的基础URL路径
     */
    private String basePath;

    /**
     * 服务描述，说明服务的用途和功能
     */
    private String description;

    /**
     * 状态（0=禁用，1=启用）
     */
    private Integer status;

    /**
     * 扩展信息（JSON格式），存储额外属性
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

    /**
     * 获取服务配置唯一标识
     *
     * @return 配置ID
     */
    public Long getId() { return id; }

    /**
     * 设置服务配置唯一标识
     *
     * @param id 配置ID
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
     * 获取服务编码
     *
     * @return 服务编码
     */
    public String getServiceCode() { return serviceCode; }

    /**
     * 设置服务编码
     *
     * @param serviceCode 服务编码
     */
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    /**
     * 获取服务名称
     *
     * @return 服务名称
     */
    public String getName() { return name; }

    /**
     * 设置服务名称
     *
     * @param name 服务名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取基础路径
     *
     * @return 基础路径
     */
    public String getBasePath() { return basePath; }

    /**
     * 设置基础路径
     *
     * @param basePath 基础路径
     */
    public void setBasePath(String basePath) { this.basePath = basePath; }

    /**
     * 获取服务描述
     *
     * @return 服务描述
     */
    public String getDescription() { return description; }

    /**
     * 设置服务描述
     *
     * @param description 服务描述
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取状态
     *
     * @return 状态（0=禁用，1=启用）
     */
    public Integer getStatus() { return status; }

    /**
     * 设置状态
     *
     * @param status 状态
     */
    public void setStatus(Integer status) { this.status = status; }

    /**
     * 获取扩展信息
     *
     * @return 扩展信息（JSON格式）
     */
    public String getExtra() { return extra; }

    /**
     * 设置扩展信息
     *
     * @param extra 扩展信息（JSON格式）
     */
    public void setExtra(String extra) { this.extra = extra; }

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