package cn.ac.fage.accessmesh.access.user.service.domain;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.bootstrap.BootstrapGraphDefinition;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.projection.PermConstants;
import cn.ac.fage.accessmesh.access.role.entity.UserRole;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.user.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.user.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 原生账号与内置角色成员的持久化连续性守卫；不替代 QueryGate 操作者门禁。 */
@Service
public class TenantAdminContinuityDomainService {
    private final SysUserMapper users;
    private final AbstractUserMapper subjects;
    private final SubjectDomainService roles;
    private final AuditDomainService audit;
    private final ObjectMapper json;
    private final TypeResolutionService types;

    public TenantAdminContinuityDomainService(SysUserMapper users, AbstractUserMapper subjects,
                                       SubjectDomainService roles, AuditDomainService audit, ObjectMapper json,
                                       TypeResolutionService types) {
        this.users = users;
        this.subjects = subjects;
        this.roles = roles;
        this.audit = audit;
        this.json = json;
        this.types = types;
    }

    public record Member(Long bindingId, Long userId, Long relationId,
                         LocalDateTime validFrom, LocalDateTime validTo) {}
    public record Snapshot(Long tenantId, Long roleId, boolean roleEnabled, List<Member> members,
                           Set<Long> qualifiedUserIds, LocalDateTime evaluatedAt) {}

    public boolean isProtectedRole(Snapshot snapshot, Long roleId) {
        return roleId != null && roleId.equals(snapshot.roleId());
    }

    /** 与有关写操作处于同一事务；已有树锁的入口先取树锁，再取本锁。 */
    public Snapshot begin(Long tenantId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("保底管理员守卫必须在写事务中调用");
        }
        users.lockAdminContinuity(tenantId);
        return snapshot(tenantId, LocalDateTime.now());
    }

    public Snapshot snapshot(Long tenantId, LocalDateTime evaluatedAt) {
        Integer roleType = types.resolveTypeValue(tenantId, "role_type", BootstrapGraphDefinition.ADMIN_ROLE_TYPE_CODE);
        var role = roleType == null ? null : roles.findRoleByIdentity(tenantId, roleType, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);
        if (role == null) return new Snapshot(tenantId, null, false, List.of(), Set.of(), evaluatedAt);
        List<UserRole> bindings = roles.findRoleBindings(tenantId, role.getId());
        Set<Long> ids = bindings.stream().map(UserRole::getAbstractUserId).collect(Collectors.toSet());
        if (ids.isEmpty()) return new Snapshot(tenantId, role.getId(), Integer.valueOf(1).equals(role.getStatus()),
            List.of(), Set.of(), evaluatedAt);
        Integer localUserType = types.resolveTypeValue(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Map<Long, SysUser> accounts = new java.util.HashMap<>();
        Set<Long> enabledSubjects = new java.util.HashSet<>();
        SqlBatches.forEach(List.copyOf(ids), batch -> {
            Set<Long> batchIds = Set.copyOf(batch);
            users.selectValidByIds(tenantId, batchIds).forEach(account -> accounts.put(account.getId(), account));
            subjects.selectValidByIds(tenantId, batchIds).stream()
                .filter(subject -> Boolean.TRUE.equals(subject.getEnabled()) && localUserType != null
                    && localUserType.equals(subject.getUserType())
                    && String.valueOf(subject.getId()).equals(subject.getExternalId()))
                .map(subject -> subject.getId()).forEach(enabledSubjects::add);
        });
        List<Member> members = bindings.stream()
            .map(binding -> new Member(binding.getId(), binding.getAbstractUserId(), binding.getRelationId(),
                binding.getValidFrom(), binding.getValidTo()))
            .sorted(Comparator.comparing(Member::bindingId)).toList();
        boolean enabled = Integer.valueOf(1).equals(role.getStatus());
        Set<Long> qualified = members.stream().filter(member -> enabled && accounts.containsKey(member.userId())
                && Integer.valueOf(1).equals(accounts.get(member.userId()).getStatus())
                && enabledSubjects.contains(member.userId()) && member.relationId() == null && member.validTo() == null
                && (member.validFrom() == null || !member.validFrom().isAfter(evaluatedAt)))
            .map(Member::userId).collect(Collectors.toSet());
        return new Snapshot(tenantId, role.getId(), enabled, members, qualified, evaluatedAt);
    }

    /** 主事务内校验最终状态，失败整体回滚；不影响无关租户或尚未初始化的固定图。 */
    public Snapshot finish(Snapshot before) {
        Snapshot after = snapshot(before.tenantId(), before.evaluatedAt());
        boolean roleRemoved = before.roleId() != null
            && (!Objects.equals(before.roleId(), after.roleId()) || (before.roleEnabled() && !after.roleEnabled()));
        if ((!before.qualifiedUserIds().isEmpty() || roleRemoved) && after.qualifiedUserIds().isEmpty()) {
            throw new BizException(AccessErrorCode.TENANT_LAST_ADMIN.getCode(), AccessErrorCode.TENANT_LAST_ADMIN.getMessage());
        }
        return after;
    }

    /** 分配入口尚无关系明细日志；撤回入口复用自身既有聚合日志，避免重复记账。 */
    public void finishMemberChange(Snapshot before) {
        Snapshot after = finish(before);
        if (!before.members().equals(after.members())) recordChanges(before, after);
    }

    private void recordChanges(Snapshot before, Snapshot after) {
        Map<Long, Member> oldRows = before.members().stream().collect(Collectors.toMap(Member::bindingId, Function.identity()));
        Map<Long, Member> newRows = after.members().stream().collect(Collectors.toMap(Member::bindingId, Function.identity()));
        Set<Long> ids = new java.util.TreeSet<>(oldRows.keySet());
        ids.addAll(newRows.keySet());
        var diff = json.createObjectNode();
        diff.put("eventType", "USER_ROLE_CHANGE");
        var items = diff.putArray("items");
        Set<Long> affected = new java.util.LinkedHashSet<>();
        for (Long id : ids) {
            Member oldRow = oldRows.get(id);
            Member newRow = newRows.get(id);
            if (Objects.equals(oldRow, newRow)) continue;
            Member member = newRow == null ? oldRow : newRow;
            affected.add(member.userId());
            var item = items.addObject();
            item.put("changeType", oldRow == null ? "ADD" : newRow == null ? "REMOVE" : "UPDATE");
            item.put("userId", member.userId());
            item.put("bindingId", id);
            var role = item.putObject("role");
            role.put("roleTypeCode", "BASIC_ROLE");
            role.put("roleExternalId", BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);
            item.set("before", json.valueToTree(oldRow));
            item.set("after", json.valueToTree(newRow));
        }
        Long roleId = after.roleId() == null ? before.roleId() : after.roleId();
        audit.recordChangeLog(new AuditDomainService.ChangeLogContext(before.tenantId(), OperatorContext.getOperatorId(),
            OperatorContext.getRequestId(), PermConstants.MaintainSource.MANUAL, "tenant-admin-membership"),
            List.of(new AuditDomainService.ChangeLogEntry("user_role", 0L, "UPDATE",
                json.writeValueAsString(before.members()), json.writeValueAsString(after.members()),
                json.writeValueAsString(diff), affected.toArray(Long[]::new), new Long[]{roleId})));
    }
}
