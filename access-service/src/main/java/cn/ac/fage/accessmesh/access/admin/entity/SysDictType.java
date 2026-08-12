package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统字典类型实体类
 * <p>
 * 对应数据库表sys_dict_type，用于存储字典类型定义。
 * 每个字典类型包含多条字典数据项。
 * </p>
 */
@Getter
@Setter
@Table("sys_dict_type")
public class SysDictType {

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
     * 字典类型编码
     */
    private String dictType;

    /**
     * 字典类型名称
     */
    private String dictName;

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
}