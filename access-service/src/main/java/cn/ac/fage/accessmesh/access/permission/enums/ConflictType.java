package cn.ac.fage.accessmesh.access.permission.enums;

import lombok.Getter;

/**
 * 权限冲突类型枚举
 * <p>
 * 定义权限冲突规则的类型分类。
 * 冲突规则用于处理多个角色权限合并时的策略。
 * </p>
 */
@Getter
public enum ConflictType {
    /**
     * 角色互斥
     * <p>
     * 表示两个角色不能同时授予同一用户。
     * 当用户拥有其中一个角色时，不能再授予另一个互斥角色。
     * 例如：管理员角色与审计角色可能互斥，避免权限滥用。
     * </p>
     */
    ROLE_MUTEX("ROLE_MUTEX", "角色互斥"),

    /**
     * 权限互斥
     * <p>
     * 表示两个权限不能同时授予同一用户。
     * 当用户拥有其中一个权限时，另一个权限自动失效或拒绝。
     * 用于处理细粒度的权限冲突场景。
     * </p>
     */
    PERM_MUTEX("PERM_MUTEX", "权限互斥");

    private final String value;
    private final String label;

    /**
     * 构造函数
     *
     * @param value 类型编码，对应数据库存储值
     * @param label 类型标签，用于显示
     */
    ConflictType(String value, String label) {
        this.value = value;
        this.label = label;
    }
}