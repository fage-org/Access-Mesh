package cn.ac.fage.accessmesh.example.data.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.data.domain.DemoPolicyDomainService;
import cn.ac.fage.accessmesh.example.data.entity.DemoPolicy;
import cn.ac.fage.accessmesh.example.data.mapper.DemoPolicyMapper;
import cn.ac.fage.accessmesh.example.data.policy.PolicyConfiguration;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoDatasetMapper;
import org.springframework.stereotype.Service;
import java.util.HashSet;

@Service
public class DemoPolicyDomainServiceImpl implements DemoPolicyDomainService {
    private final DemoPolicyMapper policies;
    private final DemoDatasetMapper datasets;
    private final DemoMetadataDomainService metadata;
    public DemoPolicyDomainServiceImpl(DemoPolicyMapper policies,DemoDatasetMapper datasets,DemoMetadataDomainService metadata) {
        this.policies=policies; this.datasets=datasets; this.metadata=metadata;
    }
    @Override
    public DemoPolicy current(long tenant) {
        var policy=policies.current(tenant);
        if (policy==null) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
        return policy;
    }
    @Override
    public DemoPolicy save(long tenant,long actor,long expectedVersion,PolicyConfiguration configuration) {
        long dataset=metadata.lockDataset(tenant,actor);
        if (datasets.policyVersion(tenant)!=expectedVersion) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
        var rowTypes=new HashSet<String>();
        rowTypes.add(configuration.read().rowResourceTypeCode()); rowTypes.add(configuration.edit().rowResourceTypeCode());
        metadata.validatePolicyTypes(tenant,configuration.read().fieldResourceTypeCode(),rowTypes);
        var scopes=metadata.scopes(tenant,configuration.bindings().keySet());
        if (scopes.size()!=configuration.bindings().size() || scopes.stream().anyMatch(s -> !s.getEnabled())) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
        if (scopes.stream().anyMatch(s -> !"PUBLISHED".equals(s.getPublicationStatus()))) throw error(ExampleErrorCode.DEMO_RESOURCE_NOT_READY);
        if (policies.storedLength(configuration.json())>262144) throw error(ExampleErrorCode.DEMO_LIMIT_EXCEEDED);
        var policy=new DemoPolicy();
        policy.setTenantId(tenant); policy.setDatasetId(dataset); policy.setVersion(expectedVersion+1);
        policy.setConfiguration(configuration.json()); policy.setChangeSummary("更新字段目录标志、操作绑定与范围谓词"); policy.setCreatedBy(actor);
        policies.insert(policy);
        if (datasets.activatePolicy(tenant,expectedVersion,expectedVersion+1,actor)!=1) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
        return current(tenant);
    }
    private static BizException error(ExampleErrorCode error) { return new BizException(error.getCode(),error.getMessage()); }
}
