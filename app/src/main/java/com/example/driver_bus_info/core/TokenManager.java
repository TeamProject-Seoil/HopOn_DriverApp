package com.example.driver_bus_info.core;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {

    private static final String PREF = "auth_prefs";

    private static final String K_ACCESS   = "accessToken";
    private static final String K_REFRESH  = "refreshToken";
    private static final String K_TTYPE    = "tokenType";
    private static final String K_ROLE     = "role";
    private static final String K_DEVICEID = "deviceId";
    private static final String K_AUTO     = "autoLogin";

    private static TokenManager instance;
    private final SharedPreferences sp;

    private TokenManager(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized TokenManager get(Context ctx) {
        if (instance == null) instance = new TokenManager(ctx);
        return instance;
    }

    // ===== 저장/조회 =====

    public String accessToken()  { return sp.getString(K_ACCESS, null); }
    public String refreshToken() { return sp.getString(K_REFRESH, null); }
    public String tokenType()    { return sp.getString(K_TTYPE,  "Bearer"); }
    public String role()         { return sp.getString(K_ROLE,   null); }
    public String deviceId()     { return sp.getString(K_DEVICEID, null); }

    public void setDeviceId(String id) {
        sp.edit().putString(K_DEVICEID, id).apply();
    }

    public void setAutoLogin(boolean on) {
        sp.edit().putBoolean(K_AUTO, on).apply();
    }

    /** ★ 자동로그인 플래그 조회 */
    public boolean isAutoLogin() {
        return sp.getBoolean(K_AUTO, false);
    }

    /** access/tokenType 갱신 (로그인/리프레시 공통) */
    public void updateAccess(String access, String tokenType) {
        sp.edit()
                .putString(K_ACCESS, access)
                .putString(K_TTYPE, tokenType == null ? "Bearer" : tokenType)
                .apply();
    }

    /** 로그인 성공시 (자동로그인 ON일 때 사용) */
    public void saveLogin(String access, String refresh, String tokenType, String role) {
        sp.edit()
                .putString(K_ACCESS, access)
                .putString(K_REFRESH, refresh)
                .putString(K_TTYPE, tokenType == null ? "Bearer" : tokenType)
                .putString(K_ROLE,  role)
                .apply();
    }

    /** ★ refresh만 덮어쓰기 (리프레시 응답이 새 토큰일 때) */
    public void saveRefreshOnly(String refresh) {
        sp.edit().putString(K_REFRESH, refresh).apply();
    }

    /** ★ refresh 제거 (자동로그인 OFF 상황) */
    public void clearRefresh() {
        sp.edit().remove(K_REFRESH).apply();
    }

    /** 전체 클리어 (로그아웃 등) */
    public void clear() {
        sp.edit().remove(K_ACCESS)
                .remove(K_REFRESH)
                .remove(K_TTYPE)
                .remove(K_ROLE)
                .remove(K_DEVICEID)
                .remove(K_AUTO)
                .apply();
    }
}
