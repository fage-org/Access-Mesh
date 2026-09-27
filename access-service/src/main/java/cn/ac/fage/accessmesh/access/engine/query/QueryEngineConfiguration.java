package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 新查询引擎装配（T-PERM-089 消费者迁移起注册 Bean）。
 * <p>
 * 引擎与读取/审计部件的构造器为包私有（结构约束收口在 engine.query 包内），
 * 本配置与被装配类同包。Clock 沿进程本地时钟语义（EPP 强制 UTC 后即 UTC）。
 * QueryEngineMetrics 维持 no-op——Micrometer 绑定随 T-PERM-094 观测演练接线
 * （2026-09-27 用户拍板，设计 §6.1 实施注「随 089+/094」按后读执行）。
 * </p>
 */
@Configuration
public class QueryEngineConfiguration {

    @Bean
    public QueryExecutionEngine queryExecutionEngine(
            TypeResolutionService typeResolutionService,
            OperationPermissionDomainService operationPermissionDomainService,
            ResourceEntityDomainService resourceEntityDomainService,
            AbstractRoleMapper abstractRoleMapper,
            RoleResourcePermissionMapper roleResourcePermissionMapper,
            CacheService cacheService,
            RolePermEntryMapper rolePermEntryMapper,
            SubjectDomainService subjectDomainService,
            PermissionConditionDomainService permissionConditionDomainService,
            PermissionConflictDomainService permissionConflictDomainService,
            ResourceEntityMapper resourceEntityMapper,
            AuditDomainService auditDomainService) {
        QueryReadSupport readSupport = new QueryReadSupport(typeResolutionService,
            operationPermissionDomainService, resourceEntityDomainService, abstractRoleMapper,
            roleResourcePermissionMapper, cacheService, rolePermEntryMapper);
        QueryAuditCollector auditCollector = new QueryAuditCollector(auditDomainService, QueryEngineMetrics.noop());
        return new QueryExecutionEngine(Clock.systemDefaultZone(), readSupport, subjectDomainService,
            permissionConditionDomainService, permissionConflictDomainService, resourceEntityMapper,
            auditCollector, QueryEngineMetrics.noop());
    }
}
