package com.example.driver_bus_info.core;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {
    private static final String PREF = "auth_prefs";
    private static final String K_AT = "access_token";
    private static final String K_RT = "refresh_token";
    private static final String K_TT = "token_type";
    private static final String K_ROLE = "role";
    private static final String K_AUTO = "auto_login";
    private static final String K_DEVICE = "device_id";

    private static TokenManager INSTANCE;
    private final SharedPreferences sp;

    private TokenManager(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized TokenManager get(Context ctx) {
        if (INSTANCE == null) INSTANCE = new TokenManager(ctx);
        return INSTANCE;
    }

    public void saveLogin(String at, String rt, String tt, String role) {
        sp.edit()
                .putString(K_AT, at)
                .putString(K_RT, rt)
                .putString(K_TT, tt == null ? "Bearer" : tt)
                .putString(K_ROLE, role)
                .apply();
    }

    public String accessToken()  { return sp.getString(K_AT, null); }
    public String refreshToken() { return sp.getString(K_RT, null); }
    public String tokenType()    { return sp.getString(K_TT, "Bearer"); }
    public String role()         { return sp.getString(K_ROLE, null); }

    public void setAutoLogin(boolean on) { sp.edit().putBoolean(K_AUTO, on).apply(); }
    public boolean isAutoLogin()         { return sp.getBoolean(K_AUTO, false); }

    public void setDeviceId(String id)   { sp.edit().putString(K_DEVICE, id).apply(); }
    public String deviceId()             { return sp.getString(K_DEVICE, null); }

    public void updateAccess(String newAt, String tt) {
        sp.edit().putString(K_AT, newAt).putString(K_TT, tt == null ? "Bearer" : tt).apply();
    }

    public void clear() {
        sp.edit().remove(K_AT).remove(K_RT).remove(K_TT).remove(K_ROLE).apply();
    }
}
