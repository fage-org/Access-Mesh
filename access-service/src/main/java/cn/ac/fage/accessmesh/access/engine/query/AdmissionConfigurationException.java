package cn.ac.fage.accessmesh.access.engine.query;

/** 准入要求未知或操作目录损坏；由接口层映射配置故障，不能当普通无权限结果。 */
public final class AdmissionConfigurationException extends QueryExecutionException {
    public AdmissionConfigurationException(String message) {
        super(message, null);
    }
}
