package com.example.driver_bus_info.util;

import android.content.Context;
import android.provider.Settings;

public class DeviceInfo {
    public static String getDeviceId(Context ctx) {
        try {
            return Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            return "unknown-device";
        }
    }

    // 서버 JwtAuthenticationFilter가 aud(X-Client-Type) 비교하니 반드시 서버와 동일 문자 사용
    // USER_APP | DRIVER_APP | ADMIN_APP 중 앱 성격에 맞게 선택
    public static String getClientType() {
        return "DRIVER_APP";
    }
}
