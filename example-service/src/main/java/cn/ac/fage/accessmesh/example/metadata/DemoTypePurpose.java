package cn.ac.fage.accessmesh.example.metadata;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import java.util.Set;

/** Shared lexical/reserved-type boundary; persisted purpose checks remain under the dataset lock. */
public final class DemoTypePurpose {
    private static final Set<String> RESERVED=Set.of("DEMO_REPORT","DEMO_POLICY","DEMO_DATASET","DEMO_TASK");
    private DemoTypePurpose() {}
    public static void requireDynamic(String type) {
        if (type==null || type.length()>64 || !type.matches("[A-Z][A-Z0-9_]*") || RESERVED.contains(type)) {
            throw new BizException(ExampleErrorCode.DEMO_POLICY_INVALID.getCode(),"字段与范围类型必须是独立用途的有效类型");
        }
    }
}
