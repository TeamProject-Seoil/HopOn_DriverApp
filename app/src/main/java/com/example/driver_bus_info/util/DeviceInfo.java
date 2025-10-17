package com.example.driver_bus_info.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import com.example.driver_bus_info.activity.RegisterActivity;

import java.util.UUID;

public class DeviceInfo {

    private static final String PREF_NAME = "com.example.driver_bus_info.prefs";
    private static final String KEY_DEVICE_ID = "device_id";

    /**
     * 시스템 ANDROID_ID 반환 (가급적 직접 쓰지 말고 getOrCreateDeviceId() 사용 권장)
     */
    public static String getDeviceId(Context ctx) {
        try {
            String id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            return TextUtils.isEmpty(id) ? "unknown-device" : id;
        } catch (Exception e) {
            return "unknown-device";
        }
    }

    /**
     * 서버 JwtAuthenticationFilter가 aud(X-Client-Type) 비교하니 반드시 서버와 동일 문자 사용
     * USER_APP | DRIVER_APP | ADMIN_APP 중 앱 성격에 맞게 선택
     */
    public static String getClientType() {
        return "DRIVER_APP";
    }

    /**
     * 영구 보존되는 기기 식별자.
     * - 기존에 저장되어 있으면 그대로 반환
     * - 없으면 ANDROID_ID를 우선 사용(문제 있는 값이면 UUID 생성) 후 저장
     */
    public static String getOrCreateDeviceId(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_DEVICE_ID, null);
        if (!TextUtils.isEmpty(saved)) {
            return saved;
        }

        // 1) ANDROID_ID 우선 사용
        String androidId = getDeviceId(ctx);
        String finalId;

        // 특정 단말에서 알려진 불안정 값(아주 예전 2.2 버그: 9774d56d682e549c)이나 unknown-device는 제외
        boolean androidIdUsable =
                !TextUtils.isEmpty(androidId)
                        && !"unknown-device".equals(androidId)
                        && !"9774d56d682e549c".equalsIgnoreCase(androidId);

        if (androidIdUsable) {
            finalId = "adid-" + androidId;
        } else {
            // 2) UUID 생성
            finalId = "uuid-" + UUID.randomUUID().toString();
        }

        sp.edit().putString(KEY_DEVICE_ID, finalId).apply();
        return finalId;
    }

    /**
     * 기존 호출부 호환용 오버로드 (RegisterActivity 인자를 그대로 받도록)
     */
    public static String getOrCreateDeviceId(RegisterActivity activity) {
        return getOrCreateDeviceId((Context) activity);
    }
}
