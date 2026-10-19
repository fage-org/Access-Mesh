package cn.ac.fage.accessmesh.example.data.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import cn.ac.fage.accessmesh.example.data.domain.DemoPolicyDomainService;
import cn.ac.fage.accessmesh.example.data.entity.DemoPolicy;
import cn.ac.fage.accessmesh.example.data.dto.PolicyDtos.*;
import cn.ac.fage.accessmesh.example.data.policy.PolicyCompiler;
import cn.ac.fage.accessmesh.example.data.service.DemoPolicyAppService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DemoPolicyAppServiceImpl implements DemoPolicyAppService {
    private final DemoPolicyDomainService policies;
    private final PolicyCompiler compiler;
    private final BusinessPermChecker permissions;
    private final JsonMapper json;
    private final TransactionTemplate write;
    public DemoPolicyAppServiceImpl(DemoPolicyDomainService policies,PolicyCompiler compiler,BusinessPermChecker permissions,
                                    JsonMapper json,PlatformTransactionManager transactions) {
        this.policies=policies; this.compiler=compiler; this.permissions=permissions; this.json=json;
        write=new TransactionTemplate(transactions);
    }
    @Override
    public PolicyResp get(Caller c,GetReq r) {
        require(c,r.datasetCode());
        return view(policies.current(c.tenantId()));
    }
    @Override
    public PolicyResp save(Caller c,SaveReq r) {
        require(c,r.datasetCode());
        var configuration=compiler.parse(r.configuration());
        return write.execute(s -> view(policies.save(c.tenantId(),c.userId(),r.expectedVersion(),configuration)));
    }
    private void require(Caller c,String dataset) {
        if (!permissions.check(Long.toString(c.tenantId()),Long.toString(c.userId()),c.clientIp(),
                BusinessPermChecker.Target.of(DemoPermission.POLICY_MANAGE.type(),null,DemoPermission.POLICY_MANAGE.operation())).allowed()) {
            throw new BizException(ExampleErrorCode.PERMISSION_DENIED.getCode(),ExampleErrorCode.PERMISSION_DENIED.getMessage());
        }
        if (!"sales-records".equals(dataset)) throw new BizException(ExampleErrorCode.DEMO_POLICY_INVALID.getCode(),"数据集配置无效");
    }
    private PolicyResp view(DemoPolicy p) { return new PolicyResp("sales-records",p.getVersion(),json.readTree(p.getConfiguration()),p.getUpdatedAt()); }
}
