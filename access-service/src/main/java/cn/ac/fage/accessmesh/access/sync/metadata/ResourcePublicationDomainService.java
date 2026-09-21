package cn.ac.fage.accessmesh.access.sync.metadata;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceManifestSync;
import cn.ac.fage.accessmesh.access.sync.SyncKeyCodecUtil;
import cn.ac.fage.accessmesh.access.sync.mapper.ResourcePublicationStateMapper;
import cn.ac.fage.accessmesh.access.sync.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import org.springframework.stereotype.Service;
import java.util.List;

/** 只承接发布账本读写；调用方持资源树锁并声明事务，失败不独立提交。 */
@Service
public class ResourcePublicationDomainService {
    private final ResourcePublicationStateMapper states;
    private final SyncMetadataMapper metadata;
    public ResourcePublicationDomainService(ResourcePublicationStateMapper states, SyncMetadataMapper metadata) {
        this.states = states;
        this.metadata = metadata;
    }
    public ResourcePublicationState read(Long tenant, String service, String scope) {
        return states.selectScope(tenant, service, SyncKeyCodecUtil.sha256Hex(scope));
    }
    public void acceptSingle(Long tenant, String service, String scope, long generation) {
        requireOne(states.acceptSingle(tenant, service, scope, SyncKeyCodecUtil.sha256Hex(scope), generation));
    }
    public void acceptFull(Long tenant, String service, String scope, long generation, String hash, boolean partial) {
        // last_full_status 与 service_manifest_sync.sync_status 共用 SUCCESS/PARTIAL 值域（常量单源）
        requireOne(states.acceptFull(tenant, service, scope, SyncKeyCodecUtil.sha256Hex(scope), generation, hash,
                partial ? ServiceManifestSync.SYNC_STATUS_PARTIAL : ServiceManifestSync.SYNC_STATUS_SUCCESS));
    }
    public void stampItem(Long tenant, String service, String scope, String businessHash, long generation, String hash) {
        requireOne(metadata.stampResourcePublication(tenant, service, SyncKeyCodecUtil.sha256Hex(scope), businessHash, generation, hash));
    }
    public void markDeleted(Long tenant, String service, String scope, List<String> hashes) {
        if (metadata.markResourcesDeleted(tenant, service, SyncKeyCodecUtil.sha256Hex(scope), hashes) != hashes.size()) {
            throw new SystemException(AccessErrorCode.SYSTEM_INIT_FAILED.getCode(), "resource cleanup metadata changed during transaction");
        }
    }
    private void requireOne(int count) {
        if (count != 1) throw new SystemException(AccessErrorCode.SYSTEM_INIT_FAILED.getCode(), "resource publication changed during transaction");
    }
}
