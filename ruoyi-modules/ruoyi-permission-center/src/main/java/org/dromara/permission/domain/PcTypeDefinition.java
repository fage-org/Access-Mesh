package org.dromara.permission.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 类型定义表 type_definition
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("type_definition")
public class PcTypeDefinition extends PermissionBaseEntity {

    @TableId("id")
    private Long id;

    private Long bizDomainId;

    private String typeKey;

    private Integer typeValue;

    private String name;

    private String description;

    private Integer sortOrder;
}
