package com.fern.platform.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SensitiveDataMasker {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "passwordhash",
            "token",
            "accesstoken",
            "refreshtoken",
            "secret",
            "payload",
            "oldvalue",
            "newvalue",
            "email",
            "phone",
            "accountnumber",
            "bankaccount",
            "nationalid",
            "taxcode",
            "ipaddress",
            "useragent"
    );

    private SensitiveDataMasker() {
    }

    public static Object mask(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> masked = new LinkedHashMap<>();
            rawMap.forEach((key, entryValue) -> {
                String normalizedKey = String.valueOf(key);
                if (isSensitive(normalizedKey)) {
                    masked.put(normalizedKey, "***");
                } else {
                    masked.put(normalizedKey, mask(entryValue));
                }
            });
            return masked;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SensitiveDataMasker::mask).toList();
        }
        return value;
    }

    public static boolean isSensitive(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String normalized = fieldName.replace("_", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized);
    }
}
