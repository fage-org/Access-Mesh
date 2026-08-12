package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作权限实体
 * <p>
 * 表示对资源可执行的操作类型，采用位掩码设计。
 * 每种操作对应一个二进制位（binaryBit），支持操作的继承（inheritMask）。
 * 常见操作包括：查看(VIEW)、创建(CREATE)、编辑(EDIT)、删除(DELETE)、管理(MANAGE)等。
 * 通过位运算可高效判断权限包含关系。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("operation_permission")
public class OperationPermission {

    /**
     * 操作权限唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 资源类型
     */
    private Integer resourceType;

    /**
     * 操作编码，用于标识操作类型
     */
    private String code;

    /**
     * 操作名称
     */
    private String name;

    /**
     * 二进制位值，用于位运算判断
     */
    private Long binaryBit;

    /**
     * 继承掩码，表示该操作隐含的其他操作权限
     */
    private Long inheritMask;

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
     * 获取有效权限位值
     * <p>
     * 计算公式：binaryBit | inheritMask。
     * 用于权限匹配的位运算判断。
     * </p>
     *
     * @return 有效权限位值
     */
    public long getEffectiveBits() {
        return (binaryBit != null ? binaryBit : 0L)
            | (inheritMask != null ? inheritMask : 0L);
    }

    /**
     * 判断权限位是否匹配目标操作
     * <p>
     * 用于判断当前权限是否包含目标操作权限。
     * 判断逻辑：(effectiveBits & target.binaryBit) != 0。
     * </p>
     *
     * @param target 目标操作权限
     * @return 如果匹配返回true，否则返回false
     */
    public boolean matchesBit(OperationPermission target) {
        if (target == null || target.binaryBit == null || target.binaryBit == 0L) {
            return false;
        }
        return (getEffectiveBits() & target.binaryBit) != 0;
    }
}