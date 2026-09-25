package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 类型—操作配对（T-PERM-082，设计 §2.3）。
 * <p>
 * 类型与操作必须保持配对：本项要求 {@code X/VIEW + Y/EDIT} 时不能拆成
 * {@code {X,Y} × {VIEW,EDIT}} 组合。身份沿用既有业务键语义，
 * 不额外 trim 或大写化改变身份（空/空白为结构错误，执行前拒绝）。
 * </p>
 *
 * @param resourceTypeCode 资源类型码，非空
 * @param operationCode    操作码，非空
 */
public record TypeOperation(String resourceTypeCode, String operationCode) {
}
