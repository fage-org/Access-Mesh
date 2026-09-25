package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 新查询契约的结构错误（T-PERM-082，设计 §2.6/§3.4）。
 * <p>
 * 空 requirements/clauses、空类型/操作、非法实体 ID、重复 key、非法组合与首版混批约束
 * 在执行前整体拒绝时抛出；外部参数错误映射保留在适配层（普通 DTO 校验不收敛到本异常）。
 * 与技术故障（DB/缓存/规则装载/预算，设计 §3.4 QueryExecutionException 面）严格分界，
 * 后者随 T-PERM-084+ 读支持落地时引入。
 * </p>
 */
public class QueryValidationException extends RuntimeException {

    public QueryValidationException(String message) {
        super(message);
    }
}
