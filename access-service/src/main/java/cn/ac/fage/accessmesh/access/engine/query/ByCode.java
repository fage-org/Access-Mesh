package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 业务编码资源引用（T-PERM-082，设计 §2.3）。
 * <p>
 * 沿既有 codeType 缺省、大小写与业务键解析语义；code 为空/空白属结构错误。
 * </p>
 *
 * @param code       资源业务编码，非空
 * @param codeType   编码类型，可空（沿既有缺省策略）
 * @param domainCode 业务域码，可空
 */
public record ByCode(String code, String codeType, String domainCode) implements ResourceRef {
}
