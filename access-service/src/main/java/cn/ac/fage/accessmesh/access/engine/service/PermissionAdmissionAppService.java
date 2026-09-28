package cn.ac.fage.accessmesh.access.engine.service;

import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;

import java.time.Duration;

/**
 * 操作准入应用服务（T-ACCESS-059，契约总册 §25.2 / 设计 §8.4）。
 * <p>
 * interface-admission 族两个 M2M 端点的服务面：在线判定（网关回源／灰度强制在线）与
 * 准入快照构建（网关本地判定）。候选一律经唯一 execute 的 ADMISSION_CANDIDATES 阶段
 * （在线=QueryItem.admission；快照=QueryItem.admissionFacts，PRESERVE+SKIP 完整保留
 * 条件分支与候选类别），操作定义固定新鲜数据库目录（§25.4），绝不从旧 GRANT_LIST
 * 评估结果或长 TTL 掩码缓存推导。
 * </p>
 */
public interface PermissionAdmissionAppService {

    /**
     * 快照建议有效期（T-ACCESS-060 边界推导：快照有效期 = 构建时刻起 15s，网关侧
     * {@code isFresh} 按服务端 expiresAt 门禁命中，L1 TTL 15s 仅作丢失广播兜底）。
     * <p>
     * 最坏陈旧窗口方程（启动校验锁，{@code PermCacheBoundaryValidator}）：
     * 上游事实族 L2 ≤10s ＋ 快照有效期 15s ≤ 30s 目标；操作定义/条件/路由/配置均为
     * 构建期新鲜库读（T-ACCESS-057/059 实核），不占预算。
     * </p>
     */
    Duration SNAPSHOT_TTL = Duration.ofSeconds(15);

    /**
     * 在线操作准入判定。
     * <p>
     * 路由匹配从该服务完整已启用路由集取全部命中（完整配置优先，不按用户权限挑较弱规则）：
     * 无注册匹配 DENY（API_NOT_REGISTERED）；多匹配异要求 20070 配置故障；同要求去重后
     * 经引擎准入评估（存在候选资格即 MAY_ENTER，恒要求业务最终检查）。
     * 服务未登记/停用按无注册路由拒绝；LEGACY_API 模式为配置故障 20071（不回落旧协议）。
     * </p>
     */
    InterfaceAdmissionResp interfaceAdmission(Long tenantId, InterfaceAdmissionReq req);

    /**
     * 构建操作准入快照。
     * <p>
     * 服务须已登记、启用且为 OPERATION_ADMISSION 模式（否则配置故障 20071）。构建期
     * 自一致校验：事务内先读配置代次→构建路由与候选→复读代次，变更即废弃重建
     * （有限重试，仍不一致按技术故障失败关闭）。routes[] 不按主体权限裁剪；
     * operationCandidates[] 按 type-operation＋条件身份＋候选类别归并，条件候选在
     * gateway_evaluable=true 且四类型白名单内时内联规则原文。
     * </p>
     */
    InterfaceAdmissionSnapshotResp interfaceAdmissionSnapshot(Long tenantId, InterfaceAdmissionSnapshotReq req);
}
