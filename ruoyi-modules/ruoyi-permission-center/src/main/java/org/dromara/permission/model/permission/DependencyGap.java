package org.dromara.permission.model.permission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DependencyGap {
    private Long resourceEntityId;
    private Long operationPermissionId;
    private List<DependencyPathNode> path = new ArrayList<>();

    public DependencyGap(Long resourceEntityId, Long operationPermissionId) {
        this.resourceEntityId = resourceEntityId;
        this.operationPermissionId = operationPermissionId;
        this.path = new ArrayList<>();
    }
}
