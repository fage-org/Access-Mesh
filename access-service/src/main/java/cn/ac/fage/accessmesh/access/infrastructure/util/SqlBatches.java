package cn.ac.fage.accessmesh.access.infrastructure.util;

import java.util.List;
import java.util.function.Consumer;

/**
 * 按 SQL 语句参数上限分批遍历（for+offset 分批样板收敛，T-PERM-079）。
 * <p>
 * 批值 500 是分批读取与分批落库的统一单源（历史各处私有 {@code SQL_BATCH_SIZE=500} 同值
 * 收敛），防 in 列表/批量写绑定参数超出 PostgreSQL 驱动单语句上限。行为与手写
 * {@code for (int offset = 0; offset < items.size(); offset += 500)} 完全等价；
 * 循环体内需要 continue 的位置在 lambda 中写作 return。
 * </p>
 */
public final class SqlBatches {

    /** 单批条数上限（全仓唯一声明点；调整需重新评估各消费点的参数量级）。 */
    public static final int BATCH_SIZE = 500;

    private SqlBatches() {}

    /**
     * 分批执行动作（读取累积/批量落库通用）；入参空列表时零次调用。
     */
    public static <T> void forEach(List<T> items, Consumer<List<T>> action) {
        for (int offset = 0; offset < items.size(); offset += BATCH_SIZE) {
            action.accept(items.subList(offset, Math.min(offset + BATCH_SIZE, items.size())));
        }
    }
}
