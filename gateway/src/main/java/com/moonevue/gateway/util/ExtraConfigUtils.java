package com.moonevue.gateway.util;

import java.util.Map;

/**
 * Utilitário para ler valores aninhados de extraConfig usando "dot path", ex.: "pix.clientId".
 */
public final class ExtraConfigUtils {
    private ExtraConfigUtils() {}

    @SuppressWarnings("unchecked")
    public static Object get(Map<String, Object> map, String path) {
        if (map == null || path == null) return null;
        String[] parts = path.split("\\.");
        Object current = map;
        for (String p : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(p);
            if (current == null) return null;
        }
        return current;
    }

    public static String getString(Map<String, Object> map, String path, String def) {
        Object v = get(map, path);
        return v != null ? String.valueOf(v) : def;
    }

    public static String requireString(Map<String, Object> map, String path, String label) {
        String v = getString(map, path, null);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Configuração obrigatória ausente: " + label + " (path=" + path + ")");
        }
        return v;
    }
}
