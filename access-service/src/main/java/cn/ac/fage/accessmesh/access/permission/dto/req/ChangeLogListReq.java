package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 变更日志列表查询请求体
 * <p>
 * 用于查询权限变更日志列表，支持多维度过滤（T-PERM-032 扩展，维度对齐 schema 索引），
 * 支持标准分页。时间语义同操作日志：ISO 无偏移墙钟字符串（全链路 UTC，§7.4），
 * 前端提交数字与表格原样展示数字对齐。
 * </p>
 *
 * @param entityType     实体类型，可选，用于过滤
 * @param entityId       实体ID，可选，用于过滤
 * @param eventType      diff_snapshot.eventType，可选（单选；空白规整为 null）
 * @param changeSource   变更来源（MANUAL/SERVICE_SYNC），可选（空白规整为 null）
 * @param affectedUserId 受影响用户 ID（affected_abstract_user_ids 包含匹配），可选
 * @param affectedRoleId 受影响角色 ID（affected_abstract_role_ids 包含匹配），可选
 * @param since          创建时间下界（含），可选
 * @param until          创建时间上界（含），可选
 * @param pageNum        页码，必填
 * @param pageSize       每页条数，必填
 */
public record ChangeLogListReq(
    String entityType,
    Long entityId,
    String eventType,
    String changeSource,
    Long affectedUserId,
    Long affectedRoleId,
    LocalDateTime since,
    LocalDateTime until,
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}