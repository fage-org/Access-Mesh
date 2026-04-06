package org.dromara.permission.model.permission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DependencyPathNode {
    private Long resourceEntityId;
    private Long operationPermissionId;
}
