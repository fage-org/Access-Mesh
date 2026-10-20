package cn.ac.fage.accessmesh.example.data.security;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.perm.ExamplePermissionProperties;
import cn.ac.fage.accessmesh.example.data.policy.PolicyConfiguration.Operations;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.ScopeGroup;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.springframework.stereotype.Component;
import feign.FeignException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

@Component
public class DemoDataPermissions {
    private final PermissionFeignClient client;
    private final ExamplePermissionProperties credentials;
    public DemoDataPermissions(PermissionFeignClient client,ExamplePermissionProperties credentials) {
        this.client=client; this.credentials=credentials;
    }
    public void require(Caller caller,DemoPermission permission,String code) {
        R<cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp> response;
        try {
            var credential=credentials.require(Long.toString(caller.tenantId()));
            response=client.checkAuth(new cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq("LOCAL_USER",Long.toString(caller.userId()),
                permission.type(),code,permission.operation(),null,null,null,null,null,null,null,
                caller.clientIp()==null?null:Map.of("clientIp",caller.clientIp())),credential.credentialId(),credential.credentialSecret());
        } catch(FeignException e) { throw new DemoPermissionUnavailable(e.status()<0 || e.status()>=500); }
        catch(BizException e) { throw new DemoPermissionUnavailable(false); }
        if(response==null || response.getCode()!=200 || response.getData()==null) throw new DemoPermissionUnavailable(response!=null && response.getCode()==99999);
        if(!response.getData().allowed()) throw denied();
    }

    public Scopes query(Caller caller,String reportCode,Operations operations,DemoPermission main) {
        var types=List.of(operations.rowResourceTypeCode(),operations.fieldResourceTypeCode());
        var operationSet=new LinkedHashSet<String>();
        operationSet.add(operations.rowOperationCode()); operationSet.add(operations.fieldOperationCode());
        var request=new QueryScopesReq("LOCAL_USER",Long.toString(caller.userId()),main.type(),reportCode,"default",
            List.of(main.operation()),types,List.copyOf(operationSet),null,null,
            caller.clientIp()==null?null:Map.of("clientIp",caller.clientIp()));
        R<QueryScopesResp> envelope;
        try {
            var credential=credentials.require(Long.toString(caller.tenantId()));
            envelope=client.queryScopes(request,credential.credentialId(),credential.credentialSecret());
        } catch (FeignException e) { throw new DemoPermissionUnavailable(e.status()<0 || e.status()>=500); }
        catch (BizException e) { throw new DemoPermissionUnavailable(false); }
        if (envelope==null || envelope.getCode()!=200 || envelope.getData()==null) {
            throw new DemoPermissionUnavailable(envelope!=null && envelope.getCode()==99999);
        }
        var response=envelope.getData();
        if (response.scopeGroups()==null || response.matchedParentOperations()==null) throw new DemoPermissionUnavailable(false);
        Set<Cell> expected=new java.util.HashSet<>();
        for(String type:types) for(String operation:operationSet) expected.add(new Cell(type,operation));
        if (response.reason()!=null) {
            if(!response.matchedParentOperations().isEmpty()) throw new DemoPermissionUnavailable(false);
            if(!response.scopeGroups().isEmpty()) {
                Set<Cell> deniedCells=new java.util.HashSet<>();
                for(var group:response.scopeGroups()) {
                    if(group==null || group.scopeMode()!=ScopeMode.DENIED || group.items()==null || !group.items().isEmpty()
                        || !deniedCells.add(new Cell(group.resourceTypeCode(),group.operationCode()))) throw new DemoPermissionUnavailable(false);
                }
                if(!deniedCells.equals(expected)) throw new DemoPermissionUnavailable(false);
            }
            switch(response.reason()) {
                case "USER_NOT_FOUND", "NO_PERMISSION" -> throw denied();
                case "OBJECT_KEY_NOT_FOUND" -> throw new BizException(ExampleErrorCode.DEMO_RESOURCE_NOT_READY.getCode(),ExampleErrorCode.DEMO_RESOURCE_NOT_READY.getMessage());
                default -> throw new DemoPermissionUnavailable(false);
            }
        }
        if (!response.matchedParentOperations().contains(main.operation())) throw denied();
        Map<Cell,ScopeGroup> groups=new HashMap<>();
        for(var group:response.scopeGroups()) {
            if (group==null || group.scopeMode()==null || group.items()==null) throw new DemoPermissionUnavailable(false);
            var key=new Cell(group.resourceTypeCode(),group.operationCode());
            if (!expected.contains(key) || groups.put(key,group)!=null) throw new DemoPermissionUnavailable(false);
            if ((group.scopeMode()==ScopeMode.INSTANCE)==group.items().isEmpty()) throw new DemoPermissionUnavailable(false);
            var itemKeys=new java.util.HashSet<Item>();
            for(var item:group.items()) {
                if (item==null || item.resourceCode()==null || item.resourceCode().isBlank()
                        || !itemKeys.add(new Item(item.resourceCode(),codeType(item.codeType())))) throw new DemoPermissionUnavailable(false);
            }
        }
        if (!groups.keySet().equals(expected)) throw new DemoPermissionUnavailable(false);
        var rows=groups.get(new Cell(operations.rowResourceTypeCode(),operations.rowOperationCode()));
        var fields=groups.get(new Cell(operations.fieldResourceTypeCode(),operations.fieldOperationCode()));
        if (rows.scopeMode()==ScopeMode.DENIED || fields.scopeMode()==ScopeMode.DENIED) throw denied();
        if (rows.items().size()>4096 || fields.items().size()>4096) throw new BizException(ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getCode(),ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getMessage());
        return new Scopes(rows,fields);
    }
    public static String codeType(String value) { return value==null || value.isBlank()?"default":value.trim(); }
    private static BizException denied() { return new BizException(ExampleErrorCode.PERMISSION_DENIED.getCode(),ExampleErrorCode.PERMISSION_DENIED.getMessage()); }
    public record Scopes(ScopeGroup rows,ScopeGroup fields) {}
    private record Cell(String type,String operation) {}
    private record Item(String code,String codeType) {}
}
