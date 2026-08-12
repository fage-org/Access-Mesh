package cn.ac.fage.accessmesh.access.admin.sync.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 同步任务强类型信封
 * <p>
 * S4 任务生产器的入参契约，封装一次实时/全量同步事件的完整信息。
 * 由 {@code SyncTaskBuilder} 按业务事件构造，由 {@code SyncTaskDomainService.enqueue}
 * 负责落地到 {@code sys_sync_task}。
 * </p>
 * <p>
 * 字段对齐 {@code docs/design/permission-center/api-contract.md}：
 * </p>
 * <ul>
 *   <li>{@code syncAction}：四类同步动作之一（{@code PERM_ABSTRACT_USER_SYNC} /
 *       {@code PERM_ABSTRACT_ROLE_SYNC} / {@code PERM_USER_ROLE_SYNC} /
 *       {@code PERM_RESOURCE_ENTITY_SYNC}）。</li>
 *   <li>{@code businessKey}：§6.2.2.4 规范化业务键原文。</li>
 *   <li>{@code batchKey}：实时同步留 {@code null}；S6 全量校准批次使用。</li>
 *   <li>{@code payload}：JSON 原文，匹配各 sync 接口的强类型 DTO。</li>
 *   <li>{@code payloadVersion}：契约版本号；handler 拒绝高于自身支持版本。</li>
 *   <li>{@code displayAttrs}：仅供 UI/审计展示，<b>严禁</b>用于执行路由。</li>
 *   <li>{@code syncOccurredAt + syncSequenceNo}：源事件版本，参与乱序判断。</li>
 *   <li>{@code phase}：实时同步可为 {@code null}；全量校准使用阶段枚举。</li>
 *   <li>{@code messageKey}：单事件唯一；为 {@code null} 时由 DomainService 生成 UUID。</li>
 * </ul>
 *
 * @param syncAction       同步动作枚举（必填）
 * @param businessKey      §6.2.2.4 业务键原文（必填）
 * @param batchKey         全量校准批次键；实时同步为 {@code null}
 * @param payload          请求体 JSON 原文（必填）
 * @param payloadVersion   契约版本号（默认 1）
 * @param displayAttrs     仅供展示的冗余信息（如 entityType/externalId/operationType）
 * @param syncOccurredAt   事件发生时间（必填）
 * @param syncSequenceNo   事件序号（必填）
 * @param phase            执行阶段（实时同步可为 {@code null}）
 * @param messageKey       事件唯一键；{@code null} 时由 DomainService 生成 UUID
 */
public record SyncTaskEnvelope(
    String syncAction,
    String businessKey,
    String batchKey,
    String payload,
    int payloadVersion,
    Map<String, Object> displayAttrs,
    LocalDateTime syncOccurredAt,
    long syncSequenceNo,
    String phase,
    String messageKey
) {
}
