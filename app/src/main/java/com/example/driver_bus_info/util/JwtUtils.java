package com.example.driver_bus_info.util;

import android.util.Base64;
import org.json.JSONObject;

public class JwtUtils {
    public static String getClaim(String jwt, String key) {
        JSONObject obj = payload(jwt);
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null;
        return String.valueOf(obj.opt(key));
    }
    public static Long getLongClaim(String jwt, String key) {
        JSONObject obj = payload(jwt);
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null;
        try { return obj.getLong(key); } catch (Exception e) {
            try { return Long.parseLong(obj.getString(key)); } catch (Exception ignore) { return null; }
        }
    }
    private static JSONObject payload(String jwt) {
        if (jwt == null) return null;
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP));
            return new JSONObject(payload);
        } catch (Exception e) { return null; }
    }
}
