package org.dromara.permission.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("permission_condition")
public class PcPermissionCondition extends PermissionBaseEntity {

    @TableId("id")
    private Long id;

    private String code;

    private String name;

    private String conditionSource;

    private String expression;

    private String status;

    private String description;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;
}
