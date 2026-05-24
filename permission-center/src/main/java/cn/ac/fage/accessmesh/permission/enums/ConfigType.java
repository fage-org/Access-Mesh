package cn.ac.fage.accessmesh.permission.enums;

import lombok.Getter;

/**
 * 配置类型枚举
 * <p>
 * 定义域配置的类型分类。
 * 配置类型用于区分不同用途的配置项。
 * </p>
 */
@Getter
public enum ConfigType {

    /**
     * 子权限配置
     * <p>
     * 定义子权限的继承和派生配置。
     * 例如：子资源的权限继承规则。
     * </p>
     */
    SUB_PERM("SUB_PERM"),

    /**
     * 域分类配置
     * <p>
     * 定义业务域的分类范围，通过资源类型码指定该域管理的对象类型。
     * extra格式: {"resourceTypeCodes":["ORG","USER"]}
     * 全局域(global=true)的范围隐式包含未被其他域认领的资源类型，无需配置CLASSIFY。
     * </p>
     */
    CLASSIFY("CLASSIFY");

    private final String value;

    /**
     * 构造函数
     *
     * @param value 类型编码，对应数据库存储值
     */
    ConfigType(String value) {
        this.value = value;
    }
}