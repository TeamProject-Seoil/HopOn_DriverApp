package com.example.driver_bus_info.util;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenStore {
    private static final String PREF = "auth_pref";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_TOKEN_TYPE = "token_type"; // e.g., "Bearer"

    // AccessToken은 디스크에 저장하지 않고 메모리만(프로세스 종료 시 자연 삭제)
    private static volatile String volatileAccess = null;

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ===== RefreshToken (디스크) =====
    public static void saveRefresh(Context ctx, String token) {
        prefs(ctx).edit().putString(KEY_REFRESH, token).apply();
    }
    public static String getRefresh(Context ctx) {
        return prefs(ctx).getString(KEY_REFRESH, null);
    }
    public static void clearRefresh(Context ctx) {
        prefs(ctx).edit().remove(KEY_REFRESH).apply();
    }

    // ===== TokenType (옵션) =====
    public static void saveTokenType(Context ctx, String tokenType) {
        prefs(ctx).edit().putString(KEY_TOKEN_TYPE, tokenType == null ? "Bearer" : tokenType).apply();
    }
    public static String getTokenType(Context ctx) {
        return prefs(ctx).getString(KEY_TOKEN_TYPE, "Bearer");
    }
    public static void clearTokenType(Context ctx) {
        prefs(ctx).edit().remove(KEY_TOKEN_TYPE).apply();
    }

    // ===== AccessToken (메모리) =====
    public static void saveAccess(String token) {
        volatileAccess = token;
    }
    public static String getAccess() {
        return volatileAccess;
    }
    public static void clearAccess() {
        volatileAccess = null;
    }

    // 필요시 전체 정리
    public static void clearAll(Context ctx) {
        prefs(ctx).edit().clear().apply();
        volatileAccess = null;
    }

    // 편의: Authorization 헤더 문자열 만들기
    public static String bearerOrNull(Context ctx) {
        String at = getAccess();
        if (at == null) return null;
        return getTokenType(ctx) + " " + at;
    }
}
