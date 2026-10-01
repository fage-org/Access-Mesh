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
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Clock;

/**
 * 新查询引擎装配（T-PERM-089 消费者迁移起注册 Bean）。
 * <p>
 * 引擎与读取/审计部件的构造器为包私有（结构约束收口在 engine.query 包内），
 * 本配置与被装配类同包。Clock 沿进程本地时钟语义（EPP 强制 UTC 后即 UTC）。
 * T-PERM-094 起 Micrometer 绑定落地：上下文存在 {@link MeterRegistry}（actuator 引入后常态）
 * 即接 {@link MicrometerQueryEngineMetrics}，引擎与审计收集器共用同一实例；无 registry
 * 的裁剪上下文回退 no-op（2026-09-27「metrics=noop 随 094 接线」拍板的落地形态）。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(EngineLimits.class)
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
            AuditDomainService auditDomainService,
            ObjectProvider<MeterRegistry> meterRegistry,
            @Value("${accessmesh.query.candidate-index-enabled:true}") boolean candidateIndexEnabled,
            EngineLimits limits) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        QueryEngineMetrics metrics = registry == null
            ? QueryEngineMetrics.noop()
            : new MicrometerQueryEngineMetrics(registry);
        QueryReadSupport readSupport = new QueryReadSupport(typeResolutionService,
            operationPermissionDomainService, resourceEntityDomainService, abstractRoleMapper,
            roleResourcePermissionMapper, cacheService, rolePermEntryMapper);
        QueryAuditCollector auditCollector = new QueryAuditCollector(auditDomainService, metrics);
        return new QueryExecutionEngine(Clock.systemDefaultZone(), readSupport, subjectDomainService,
            permissionConditionDomainService, permissionConflictDomainService, resourceEntityMapper,
            auditCollector, metrics, candidateIndexEnabled, limits);
    }
}
