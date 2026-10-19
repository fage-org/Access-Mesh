package cn.ac.fage.accessmesh.example.metadata.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoPublicationDomainService;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.metadata.dto.PublicationDtos.*;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoPublication;
import cn.ac.fage.accessmesh.example.metadata.publication.DemoResourceSyncClient;
import cn.ac.fage.accessmesh.example.metadata.service.DemoPublicationAppService;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import cn.ac.fage.accessmesh.example.perm.ExamplePermissionProperties;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import feign.FeignException;

@Service
public class DemoPublicationAppServiceImpl implements DemoPublicationAppService {
    private final DemoPublicationDomainService publications;
    private final DemoResourceSyncClient client;
    private final ExamplePermissionProperties credentials;
    private final BusinessPermChecker permissions;
    private final JsonMapper json;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    public DemoPublicationAppServiceImpl(DemoPublicationDomainService publications, DemoResourceSyncClient client,
            ExamplePermissionProperties credentials, BusinessPermChecker permissions, JsonMapper json,
            PlatformTransactionManager transactions) {
        this.publications=publications; this.client=client; this.credentials=credentials;
        this.permissions=permissions; this.json=json;
        write=new TransactionTemplate(transactions);
        read=new TransactionTemplate(transactions);
        read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public synchronized void publishNext() {
        var event=write.execute(s -> publications.claimNext());
        if (event == null) return;
        Outcome outcome = send(event);
        write.executeWithoutResult(s -> publications.finish(event, outcome.success(), outcome.retryable(), outcome.reason()));
    }

    private Outcome send(DemoPublication event) {
        try {
            var credential=credentials.require(Long.toString(event.getTenantId()));
            var response=client.sync(json.readTree(event.getPayload()), credential.credentialId(), credential.credentialSecret());
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                return new Outcome(false, false, "INVALID_ENVELOPE");
            }
            var result=response.getData();
            if (result.accepted() && !result.stale() && result.retryClass() == null && result.detail() == null
                && (result.applied() && result.reason() == null
                    || !result.applied() && "SYNC_VERSION_REPLAYED".equals(result.reason()))) {
                return new Outcome(true, false, null);
            }
            if (!result.accepted() && !result.applied() && !result.stale()
                    && ("RETRYABLE".equals(result.retryClass()) || "DEPENDENCY_MISSING".equals(result.retryClass()))) {
                return new Outcome(false, true, "SYNC_DEPENDENCY_FAILURE");
            }
            return new Outcome(false, false, result.stale() ? "SYNC_VERSION_STALE" : "SYNC_REJECTED");
        } catch (FeignException e) {
            return new Outcome(false, e.status()<0 || e.status()>=500, "SYNC_TRANSPORT_FAILURE");
        } catch (BizException e) {
            return new Outcome(false, false, "CREDENTIAL_UNAVAILABLE");
        }
    }

    @Override
    public PublicationResp get(Caller c, PublicationGetReq r) {
        require(c);
        return view(publications.latest(c.tenantId(), r.resourceTypeCode(), r.resourceCode(), r.codeType()));
    }
    @Override
    public PublicationResp retry(Caller c, PublicationGetReq r) {
        require(c);
        return write.execute(s -> view(publications.retry(c.tenantId(), c.userId(), r.resourceTypeCode(), r.resourceCode(), r.codeType())));
    }
    @Override
    public PageResp<PublicationResp> page(Caller c, PublicationPageReq r) {
        require(c);
        return read.execute(s -> {
            var page=publications.page(c.tenantId(), r.status(), r.pageNum()==null ? 1 : r.pageNum(), r.pageSize()==null ? 20 : r.pageSize());
            return new PageResp<>(page.items().stream().map(DemoPublicationAppServiceImpl::view).toList(),
                page.total(), page.pageNum(), page.pageSize(), page.hasNext());
        });
    }
    private void require(Caller c) {
        if (!permissions.check(Long.toString(c.tenantId()), Long.toString(c.userId()), c.clientIp(),
            BusinessPermChecker.Target.of(DemoPermission.POLICY_MANAGE.type(), null, DemoPermission.POLICY_MANAGE.operation())).allowed()) {
            throw new BizException(ExampleErrorCode.PERMISSION_DENIED.getCode(), ExampleErrorCode.PERMISSION_DENIED.getMessage());
        }
    }
    private static PublicationResp view(DemoPublication e) {
        return new PublicationResp(e.getResourceTypeCode(), e.getResourceCode(), e.getCodeType(), e.getSourceVersion(), e.getStatus(), e.getAttempt(), e.getFailureReason());
    }
    private record Outcome(boolean success, boolean retryable, String reason) {}
}
