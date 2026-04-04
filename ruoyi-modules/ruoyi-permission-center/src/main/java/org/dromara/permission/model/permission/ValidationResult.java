package org.dromara.permission.model.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ValidationResult {
    private boolean valid;
    private List<String> reasons = new ArrayList<>();

    public static ValidationResult ok() {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        return result;
    }

    public static ValidationResult fail(String reason) {
        ValidationResult result = new ValidationResult();
        result.setValid(false);
        result.getReasons().add(reason);
        return result;
    }
}
