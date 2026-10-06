package cn.ac.fage.accessmesh.access.grant.service.domain;

import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.grant.enums.GrantSource;

/**
 * 自动授权种子口径与事实键构造的唯一实现（设计 §6.1）。
 * <p>
 * 物化（T-PERM-072）、来源解释/授撤预览（T-PERM-073）、对账（T-PERM-073）三方共用。
 * 种子判定漂移会让预览的 removed/added 与物化实际落库静默分叉（预览说要删的事实保存后
 * 未删，且双方各自按自己的口径测试无法察觉），口径变更必须经此单点。
 * </p>
 * <p>
 * 纯静态判定，不触达推导核心 {@link AutoGrantDerivation}——推导只消费
 * {@link AutoGrantDerivation.Fact}，不耦合持久化实体。
 * </p>
 */
public final class AutoGrantFacts {

    private AutoGrantFacts() {
    }

    /**
     * 种子口径（§6.1）：有效 MANUAL 实例主授权行——scope_all=false、实例 ID 非空、
     * depend_on=NULL、非 AUTO_DEP、非 AUTHORITY_ROOT（授权根随类型生命周期维护，
     * 不作物种）、非 BOOTSTRAP_SEED（平台初始化种子只读、不参与自动推导种子，
     * 与 dependency-auto-grant.md 来源级声明一致；当前种子恒类型级不可达，
     * 2026-10-06 逐任务评审补防御性排除）；条件不限。
     */
    public static boolean isSeed(RoleResourcePermission row) {
        return !Boolean.TRUE.equals(row.getScopeAll())
            && row.getResourceEntityId() != null
            && row.getDependOn() == null
            && !isAutoDep(row) && !isAuthorityRoot(row) && !isBootstrapSeed(row);
    }

    public static boolean isBootstrapSeed(RoleResourcePermission row) {
        return GrantSource.BOOTSTRAP_SEED.getValue().equals(row.getGrantSource());
    }

    public static boolean isAutoDep(RoleResourcePermission row) {
        return GrantSource.AUTO_DEP.getValue().equals(row.getGrantSource());
    }

    public static boolean isAuthorityRoot(RoleResourcePermission row) {
        return GrantSource.AUTHORITY_ROOT.getValue().equals(row.getGrantSource());
    }

    /** 完整事实键（资源实体 + canonical 操作位 + 条件身份；conditionId=null 为无条件变体）。 */
    public static AutoGrantDerivation.Fact factOf(RoleResourcePermission row) {
        return new AutoGrantDerivation.Fact(row.getResourceEntityId(), row.getGrantedBits(), row.getConditionId());
    }
}
