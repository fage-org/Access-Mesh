package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;

import java.util.List;
import java.util.Set;

/**
 * 聚合授权计划的唯一预检与执行入口。
 * <p>主体参数（{@code subjectId}）为权限域投影主体（{@code abstract_user.id}），
 * T-ORG-001 统一后操作者 ID 即主体 ID（{@code abstract_user.id}），无运行时转换层。</p>
 */
public interface PermissionGrantPlanDomainService {

    /**
     * 预检授权计划（含 canGrant 委托验证）
     *
     * @param tenantId   租户ID
     * @param subjectId  权限域投影主体ID（abstract_user.id）
     * @param roleId     目标角色ID
     * @param domainCode 业务域编码，可选
     * @param plan       授权计划
     * @return 预检通过的计划
     */
    PreparedGrantPlan prevalidate(Long tenantId, Long subjectId, Long roleId, String domainCode,
                                  ApplyGrantPlanReq.GrantPlan plan);

    void apply(PreparedGrantPlan preparedPlan);

    /**
     * 系统侧种子直写通道（T-PERM-062 下沉；bootstrap 固定图与类型授权根共用）。
     * <p>
     * 「在委托不变量之外建立引导」的专用入口：跳过 prevalidate/verifyDelegation
     * （无操作者、无委托校验），但保留 {@code validateSingleManualGrants} 与
     * {@code validateGrantAttributes} 两条领域校验，并复用 {@link #apply} 落库管线。
     * 幂等 insert-if-absent：与既有有效行同身份键（资源/范围+操作位+父权限+来源）的
     * 种子行跳过不写、不覆盖（operation 软删后同位重建场景旧种子行仍有效，直插会撞
     * uk_role_resource_permission 误报 20033）。
     * 仅限种子类写入调用方（bootstrap / 类型生命周期钩子）；通用授权链禁止经本入口
     * 绕过委托校验（architecture §14.1/§14.2，种子类写入措辞随 T-PERM-062 扩为
     * 「bootstrap / 类型首授」）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   种子接收角色ID
     * @param grants   种子行（grant_source 由调用方携带：bootstrap=MANUAL / 类型首授=AUTHORITY_ROOT）
     */
    void seedGrants(Long tenantId, Long roleId, List<RoleResourcePermission> grants);

    /**
     * SUB_PERM 策略唯一公开解析入口（§6.5.2）：读接口（sub-perm-allowed-types）
     * 直接序列化策略结果，写链路 prevalidate 复用同一解析器——读写同源，
     * 禁止在 AppService/Controller 另行编写 SUB_PERM 判断。
     *
     * @param tenantId               租户ID
     * @param parentResourceTypeCode 父资源类型编码
     * @return 不可变策略对象
     * @throws cn.ac.fage.accessmesh.common.exception.BizException 资源类型不存在（20007）
     */
    SubPermissionPolicy resolveSubPermissionPolicy(Long tenantId, String parentResourceTypeCode);

    /** SUB_PERM 不可变策略对象（mode/reason/允许集），判定优先级见实现与 §6.5.2 */
    record SubPermissionPolicy(Mode mode, String reason, List<String> allowedTypeCodes) {

        public enum Mode { ALLOW_ALL, ALLOW_LIST, ALLOW_NONE }

        public boolean allows(String childTypeCode) {
            if (mode == Mode.ALLOW_ALL) return true;
            if (mode != Mode.ALLOW_LIST || childTypeCode == null) return false;
            return allowedTypeCodes.stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(childTypeCode));
        }
    }

    record PreparedCreate(
        RoleResourcePermission permission,
        List<RoleResourcePermission> children
    ) {}

    /**
     * 变更日志业务键快照（§5.8 diff_snapshot 规范聚合（原 §6.8）形状）：prevalidate 期装配，
     * 供 AppService 组装 diff_snapshot 的 items[]（removes 行随后被软删，事后不可回查）。
     */
    record AuditPermissionKey(
        String changeType,
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String operationCode,
        ScopeMode scopeMode
    ) {}

    record PreparedGrantPlan(
        Long tenantId,
        Long roleId,
        List<PreparedCreate> creates,
        List<RoleResourcePermission> updates,
        List<Long> removes,
        Set<PermissionGrantDomainService.GrantCheckKey> delegationKeys,
        List<AuditPermissionKey> auditKeys,
        /** 内联回收候选（T-PERM-048）：随换绑/清除/删行失去引用的原条件 id，apply 末段判定归零回收 */
        Set<Long> inlineRecycleCandidates
    ) {}
}
