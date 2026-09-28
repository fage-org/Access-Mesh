package cn.ac.fage.accessmesh.example.report;

/**
 * 演示报表实例（T-ACCESS-061 §8.6 逐路由最终检查的示例业务实体）。
 * <p>
 * 示例资源族=EXAMPLE 类型（类型/操作/资源实体经 access-service 管理面登记，映射经
 * service-config/sync-v2 声明）；本服务内存持有数据，最终检查的目标恒为业务请求
 * 实际解析出的 {@code code}——查 A 不得按 B 取数。
 * </p>
 */
public record DemoReport(String code, String name, String content) {

    /** depend_on 父码（示例：报表明细是报表的子权限资源，见 ReportStore 种子）。 */
    public String parentCode() {
        // code 形如 report-1-detail → 父 report-1（仅种子数据按此约定，业务演示用）
        int idx = code.lastIndexOf("-detail");
        return idx > 0 ? code.substring(0, idx) : null;
    }
}
