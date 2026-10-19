package cn.ac.fage.accessmesh.example.metadata.domain;

import java.util.List;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoPublication;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

public interface DemoPublicationDomainService {
    void record(long tenant, long actor, List<Change> changes);
    DemoPublication claimNext();
    void finish(DemoPublication event, boolean successful, boolean retryable, String reason);
    DemoPublication latest(long tenant, String type, String code, String codeType);
    DemoPublication retry(long tenant, long actor, String type, String code, String codeType);
    PageResp<DemoPublication> page(long tenant, String status, int page, int size);
    record Change(String type, String code, String codeType, long version, String operation,
                  String name, boolean enabled, String entityType, String entityId) {}
}
