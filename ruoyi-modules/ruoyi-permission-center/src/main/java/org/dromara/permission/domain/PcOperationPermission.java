package org.dromara.permission.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 操作权限表 operation_permission
 * effective = binary_bit | inherit_mask
 * 逻辑删除：基类 deleteFlag，0=未删除，删除时=id
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("operation_permission")
public class PcOperationPermission extends PermissionBaseEntity {

    /** 主键 */
    @TableId("id")
    private Long id;

    /** 适用资源类型，NULL 表示适用全部资源类型 */
    private Integer resourceType;

    /** 操作编码，如 VIEW、EDIT */
    private String code;

    /** 显示名 */
    private String name;

    /** 本操作独占位，如 1、2、4、8 */
    private Long binaryBit;

    /** 继承的位掩码，实际权限=binary_bit|inherit_mask */
    private Long inheritMask;
}
