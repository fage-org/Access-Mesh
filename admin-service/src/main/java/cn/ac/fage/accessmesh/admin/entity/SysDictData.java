package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统字典数据实体类
 * <p>
 * 对应数据库表sys_dict_data，用于存储字典的具体数据项。
 * 每条数据项归属于某个字典类型，包含标签、值、排序等信息。
 * </p>
 */
@Table("sys_dict_data")
public class SysDictData {

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
     * 字典类型
     */
    private String dictType;

    /**
     * 字典标签
     */
    private String dictLabel;

    /**
     * 字典值
     */
    private String dictValue;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * CSS样式类
     */
    private String cssClass;

    /**
     * 列表样式类
     */
    private String listClass;

    /**
     * 是否默认值
     */
    private Boolean isDefault;

    /**
     * 状态（0=正常，1=禁用）
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

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

    /**
     * 获取主键ID
     *
     * @return 主键ID
     */
    public Long getId() { return id; }

    /**
     * 设置主键ID
     *
     * @param id 主键ID
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
     * 获取字典类型
     *
     * @return 字典类型
     */
    public String getDictType() { return dictType; }

    /**
     * 设置字典类型
     *
     * @param dictType 字典类型
     */
    public void setDictType(String dictType) { this.dictType = dictType; }

    /**
     * 获取字典标签
     *
     * @return 字典标签
     */
    public String getDictLabel() { return dictLabel; }

    /**
     * 设置字典标签
     *
     * @param dictLabel 字典标签
     */
    public void setDictLabel(String dictLabel) { this.dictLabel = dictLabel; }

    /**
     * 获取字典值
     *
     * @return 字典值
     */
    public String getDictValue() { return dictValue; }

    /**
     * 设置字典值
     *
     * @param dictValue 字典值
     */
    public void setDictValue(String dictValue) { this.dictValue = dictValue; }

    /**
     * 获取排序序号
     *
     * @return 排序序号
     */
    public Integer getSortOrder() { return sortOrder; }

    /**
     * 设置排序序号
     *
     * @param sortOrder 排序序号
     */
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    /**
     * 获取CSS样式类
     *
     * @return CSS样式类
     */
    public String getCssClass() { return cssClass; }

    /**
     * 设置CSS样式类
     *
     * @param cssClass CSS样式类
     */
    public void setCssClass(String cssClass) { this.cssClass = cssClass; }

    /**
     * 获取列表样式类
     *
     * @return 列表样式类
     */
    public String getListClass() { return listClass; }

    /**
     * 设置列表样式类
     *
     * @param listClass 列表样式类
     */
    public void setListClass(String listClass) { this.listClass = listClass; }

    /**
     * 获取是否默认值
     *
     * @return 是否默认值
     */
    public Boolean getIsDefault() { return isDefault; }

    /**
     * 设置是否默认值
     *
     * @param isDefault 是否默认值
     */
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    /**
     * 获取状态
     *
     * @return 状态
     */
    public Integer getStatus() { return status; }

    /**
     * 设置状态
     *
     * @param status 状态
     */
    public void setStatus(Integer status) { this.status = status; }

    /**
     * 获取备注
     *
     * @return 备注
     */
    public String getRemark() { return remark; }

    /**
     * 设置备注
     *
     * @param remark 备注
     */
    public void setRemark(String remark) { this.remark = remark; }

    /**
     * 获取创建人ID
     *
     * @return 创建人ID
     */
    public Long getCreatedBy() { return createdBy; }

    /**
     * 设置创建人ID
     *
     * @param createdBy 创建人ID
     */
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    /**
     * 获取更新人ID
     *
     * @return 更新人ID
     */
    public Long getUpdatedBy() { return updatedBy; }

    /**
     * 设置更新人ID
     *
     * @param updatedBy 更新人ID
     */
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    /**
     * 获取删除人ID
     *
     * @return 删除人ID
     */
    public Long getDeletedBy() { return deletedBy; }

    /**
     * 设置删除人ID
     *
     * @param deletedBy 删除人ID
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
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * 设置更新时间
     *
     * @param updatedAt 更新时间
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
     * @return 删除标记
     */
    public Long getDeleteFlag() { return deleteFlag; }

    /**
     * 设置删除标记
     *
     * @param deleteFlag 删除标记
     */
    public void setDeleteFlag(Long deleteFlag) { this.deleteFlag = deleteFlag; }
}