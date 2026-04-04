package org.dromara.permission.model.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DependencyCheckResult {
    private boolean satisfied;
    private List<DependencyGap> gaps = new ArrayList<>();

    public static DependencyCheckResult ok() {
        DependencyCheckResult result = new DependencyCheckResult();
        result.setSatisfied(true);
        return result;
    }

    public static DependencyCheckResult fail(List<DependencyGap> gaps) {
        DependencyCheckResult result = new DependencyCheckResult();
        result.setSatisfied(false);
        result.setGaps(gaps);
        return result;
    }
}
