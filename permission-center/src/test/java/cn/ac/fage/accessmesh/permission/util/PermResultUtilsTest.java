package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermResultUtilsTest {

    @Test
    void toQueryResourcesRespShouldResolveOperationByResourceTypeAndBinaryBit() {
        ResourceEntity menuResource = resource(1L, 1, "menu:view", "菜单查看");
        ResourceEntity userResource = resource(2L, 2, "user:create", "用户创建");

        RolePermEntry menuEntry = new RolePermEntry(11L, 101L, 1L, "menu:view", 1, 1L, null, null, "MANUAL", false, null, false, null);
        RolePermEntry userEntry = new RolePermEntry(12L, 102L, 2L, "user:create", 2, 1L, null, null, "MANUAL", false, null, false, null);

        OperationPermission userCreate = operation(201L, 2, "CREATE", 1L, 0L);
        OperationPermission menuView = operation(101L, 1, "VIEW", 1L, 0L);
        Map<Long, OperationPermission> operationMap = new LinkedHashMap<>();
        operationMap.put(userCreate.getId(), userCreate);
        operationMap.put(menuView.getId(), menuView);

        PermResult result = PermResult.builder(true, null)
            .instanceEntries(List.of(menuEntry, userEntry))
            .resourceMap(Map.of(menuResource.getId(), menuResource, userResource.getId(), userResource))
            .operationMap(operationMap)
            .build();

        QueryResourcesResp response = PermResultUtils.toQueryResourcesResp(result, 60);
        Map<String, QueryResourcesResp.ResourceEntry> entriesByCode = response.items().stream()
            .collect(Collectors.toMap(QueryResourcesResp.ResourceEntry::resourceCode, Function.identity()));

        assertEquals(List.of("VIEW"), entriesByCode.get("menu:view").operations());
        assertEquals(List.of("CREATE"), entriesByCode.get("user:create").operations());
    }

    private ResourceEntity resource(Long id, Integer resourceType, String code, String name) {
        ResourceEntity resourceEntity = new ResourceEntity();
        resourceEntity.setId(id);
        resourceEntity.setResourceType(resourceType);
        resourceEntity.setCode(code);
        resourceEntity.setCodeType("default");
        resourceEntity.setName(name);
        resourceEntity.setDeleteFlag(0L);
        return resourceEntity;
    }

    private OperationPermission operation(Long id, Integer resourceType, String code, Long binaryBit, Long inheritMask) {
        OperationPermission operationPermission = new OperationPermission();
        operationPermission.setId(id);
        operationPermission.setResourceType(resourceType);
        operationPermission.setCode(code);
        operationPermission.setBinaryBit(binaryBit);
        operationPermission.setInheritMask(inheritMask);
        operationPermission.setDeleteFlag(0L);
        return operationPermission;
    }
}