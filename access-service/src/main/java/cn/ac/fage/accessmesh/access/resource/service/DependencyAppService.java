package cn.ac.fage.accessmesh.access.resource.service;

import cn.ac.fage.accessmesh.access.resource.dto.req.AutoGrantExplainReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.DependencyDeclarationStatusReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.access.resource.dto.resp.AutoGrantExplainResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.DependencyDeclarationStatusResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.ResourceDependencyResp;
import java.util.List;

/** 编译依赖图的只读管理查询；写入仅由 MANIFEST 编译器承担。 */
public interface DependencyAppService {
    List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId);
    List<ResourceDependencyResp> listAllDependencies(Long tenantId);
    boolean hasDependencyCycle(Long tenantId, ResourceDependencyCheckReq req);

    /**
     * 角色自动授权来源解释（T-PERM-073，契约 §12.3.1）——只读共享 DAG。
     * <p>门禁 DEPENDENCY:VIEW 类型级 + 角色业务键解析；REPEATABLE_READ 只读一致视图；
     * 输出限额只截断展示，漂移显式标识，不把「应生成」当「已生效」。</p>
     */
    AutoGrantExplainResp explainAutoGrant(Long tenantId, AutoGrantExplainReq req);

    /**
     * 依赖声明诊断（T-PERM-073，契约 §12.3 declaration-status）——只读。
     * <p>每服务 manifest 发布状态 + 声明行（含 REJECTED 原因）；变更由所属服务
     * manifest 发布承担，本面不提供修改。</p>
     */
    DependencyDeclarationStatusResp declarationStatus(Long tenantId, DependencyDeclarationStatusReq req);
}
