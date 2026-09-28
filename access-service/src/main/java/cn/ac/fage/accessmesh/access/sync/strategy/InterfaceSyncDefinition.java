package cn.ac.fage.accessmesh.access.sync.strategy;

import cn.ac.fage.accessmesh.access.resource.dto.RequiredPermission;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigSyncV2Req;
import java.util.List;

/** 两种线协议适配到同一同步事务；版本只表明声明形态，不切换鉴权模式。 */
public record InterfaceSyncDefinition(String serviceCode, String basePath, String syncMode,
                                      List<Group> groups, int version) {
    public record Group(String groupCode, String groupName, List<Api> apis) {}
    public record Api(String name, String httpMethod, String path, String resourceCode,
                      String description, RequiredPermission requiredPermission) {}

    public static InterfaceSyncDefinition from(ServiceConfigSyncReq req) {
        return new InterfaceSyncDefinition(req.serviceCode(), req.basePath(), req.syncMode(),
            req.groups().stream().map(g -> new Group(g.groupCode(), g.groupName(), g.apis().stream()
                .map(a -> new Api(a.name(), a.httpMethod(), a.path(), a.resourceCode(), a.description(), null)).toList()))
                .toList(), 1);
    }

    public static InterfaceSyncDefinition from(ServiceConfigSyncV2Req req) {
        return new InterfaceSyncDefinition(req.serviceCode(), req.basePath(), req.syncMode(),
            req.groups().stream().map(g -> new Group(g.groupCode(), g.groupName(), g.apis().stream()
                .map(a -> new Api(a.name(), a.httpMethod(), a.path(), a.resourceCode(), a.description(), a.requiredPermission())).toList()))
                .toList(), 2);
    }
}
