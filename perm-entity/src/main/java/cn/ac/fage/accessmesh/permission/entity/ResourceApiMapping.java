package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 资源API映射实体
 * <p>
 * 表示资源与HTTP API接口的映射关系。
 * 定义哪个API接口对应哪个权限资源。
 * 支持HTTP方法、路径模式匹配和匹配顺序。
 * 用于API级别的权限校验，确保API访问受权限控制。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("resource_api_mapping")
public class ResourceApiMapping {

    /**
     * 资源API映射唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 所属业务域ID
     */
    private Long bizDomainId;

    /**
     * 资源实体ID
     */
    private Long resourceEntityId;

    /**
     * 服务编码，标识API所属的服务
     */
    private String serviceCode;

    /**
     * HTTP方法（GET/POST/PUT/DELETE等）
     */
    private String httpMethod;

    /**
     * 路径模式，支持Ant风格路径匹配
     */
    private String pathPattern;

    /**
     * 匹配顺序，用于多个匹配规则时的优先级判断
     */
    private Integer matchOrder;

    /**
     * 启用状态（true=启用，false=禁用）
     */
    private Boolean enabled;

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
     * 获取资源API映射唯一标识
     *
     * @return 映射ID
     */
    public Long getId() { return id; }

    /**
     * 设置资源API映射唯一标识
     *
     * @param id 映射ID
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
     * 获取所属业务域ID
     *
     * @return 业务域ID
     */
    public Long getBizDomainId() { return bizDomainId; }

    /**
     * 设置所属业务域ID
     *
     * @param bizDomainId 业务域ID
     */
    public void setBizDomainId(Long bizDomainId) { this.bizDomainId = bizDomainId; }

    /**
     * 获取资源实体ID
     *
     * @return 资源实体ID
     */
    public Long getResourceEntityId() { return resourceEntityId; }

    /**
     * 设置资源实体ID
     *
     * @param resourceEntityId 资源实体ID
     */
    public void setResourceEntityId(Long resourceEntityId) { this.resourceEntityId = resourceEntityId; }

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
     * 获取HTTP方法
     *
     * @return HTTP方法（GET/POST/PUT/DELETE等）
     */
    public String getHttpMethod() { return httpMethod; }

    /**
     * 设置HTTP方法
     *
     * @param httpMethod HTTP方法
     */
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    /**
     * 获取路径模式
     *
     * @return 路径模式，支持Ant风格路径匹配
     */
    public String getPathPattern() { return pathPattern; }

    /**
     * 设置路径模式
     *
     * @param pathPattern 路径模式
     */
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

    /**
     * 获取匹配顺序
     *
     * @return 匹配顺序
     */
    public Integer getMatchOrder() { return matchOrder; }

    /**
     * 设置匹配顺序
     *
     * @param matchOrder 匹配顺序
     */
    public void setMatchOrder(Integer matchOrder) { this.matchOrder = matchOrder; }

    /**
     * 获取启用状态
     *
     * @return 启用状态（true=启用，false=禁用）
     */
    public Boolean getEnabled() { return enabled; }

    /**
     * 设置启用状态
     *
     * @param enabled 启用状态
     */
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

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