package cn.ac.fage.accessmesh.access.infrastructure.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 * <p>
 * 表示用户操作行为的审计日志记录。
 * 记录操作的模块、动作、目标对象、操作者信息等。
 * 用于系统审计、安全分析和操作追溯。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("operation_log")
public class OperationLog {

    /**
     * 操作日志唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 操作模块，标识操作所属的功能模块
     */
    private String module;

    /**
     * 操作动作，标识具体的操作类型
     */
    private String action;

    /**
     * 目标类型，标识操作对象的类型
     */
    private String targetType;

    /**
     * 目标ID，标识操作对象的唯一标识（T-ACCESS-002 起为字符串，兼容业务键与数值 ID）
     */
    private String targetId;

    /**
     * 操作摘要，简述操作内容
     */
    private String summary;

    /**
     * 操作者用户ID
     */
    private Long operatorId;

    /**
     * 操作者名称
     */
    private String operatorName;

    /**
     * IP地址，记录操作来源
     */
    private String ipAddress;

    /**
     * 请求ID，用于关联请求链路
     */
    private String requestId;

    /**
     * 请求URL（T-ACCESS-002 归并自 admin sys_audit_log）
     */
    private String requestUrl;

    /**
     * 请求体内容（T-ACCESS-002 归并自 admin sys_audit_log；T-ACCESS-025 操作日志参数序列化收敛起停用恒 NULL，
     * 列与字段保留兼容存量数据，不再写入）
     */
    private String requestBody;

    /**
     * 响应状态码（T-ACCESS-002 归并自 admin sys_audit_log）
     */
    private Integer responseCode;

    /**
     * 操作耗时（毫秒，T-ACCESS-002 归并自 admin sys_audit_log）
     */
    private Integer costTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}