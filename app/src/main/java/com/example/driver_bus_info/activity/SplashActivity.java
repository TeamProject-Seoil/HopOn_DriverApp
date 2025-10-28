// app/src/main/java/com/example/driver_bus_info/activity/SplashActivity.java
package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.example.driver_bus_info.R;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    private static final String PREF = "driver_prefs";
    private static final String K_OPERATION_ID = "sel_operation_id";

    private TokenManager tm;
    private ApiService api;
    private String clientType;
    private String deviceId;

    private boolean routed = false; // 가드 (재호출 방지)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start); // Splash 전용 레이아웃(OK)

        tm = TokenManager.get(this);
        api = ApiClient.get(this);
        clientType = DeviceInfo.getClientType();
        deviceId = tm.deviceId();
        if (deviceId == null) {
            deviceId = DeviceInfo.getDeviceId(this);
            tm.setDeviceId(deviceId);
        }

        new Handler().postDelayed(this::route, 1200);
    }

    private void route() {
        if (routed) return;
        routed = true;

        // ✅ 자동로그인 OFF면, 토큰이 있어도 무조건 로그인 화면
        if (!tm.isAutoLogin()) {
            goLogin();
            return;
        }

        // 자동로그인 ON + refresh 있으면 먼저 갱신
        if (tm.refreshToken() != null) {
            api.refresh(tm.refreshToken(), clientType, deviceId)
                    .enqueue(new Callback<ApiService.AuthResponse>() {
                        @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                            if (res.isSuccessful() && res.body()!=null) {
                                ApiService.AuthResponse a = res.body();
                                tm.updateAccess(a.accessToken, a.tokenType);
                                if (a.refreshToken!=null && !a.refreshToken.isEmpty()) tm.saveRefreshOnly(a.refreshToken);
                                checkActiveAndGo();
                            } else {
                                tm.setAutoLogin(false);
                                tm.clearRefresh();
                                goLogin();
                            }
                        }
                        @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                            goLogin();
                        }
                    });
            return;
        }

        // refresh는 없지만 자동로그인 ON이고 access만 있다 → 바로 운행 여부 확인
        if (tm.accessToken() != null) {
            checkActiveAndGo();
        } else {
            goLogin();
        }
    }

    /** 서버의 현재 운행 여부 체크 후 분기 */
    private void checkActiveAndGo() {
        api.getActiveOperation(null, clientType).enqueue(new Callback<ApiService.ActiveOperationResp>() {
            @Override public void onResponse(Call<ApiService.ActiveOperationResp> call, Response<ApiService.ActiveOperationResp> res) {
                SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);

                if (!res.isSuccessful()) { // 토큰 문제 등
                    goLogin();
                    return;
                }

                ApiService.ActiveOperationResp op = res.body();
                if (op != null && "RUNNING".equalsIgnoreCase(op.status)) {
                    // 운행 중 → 로컬 저장 & Driving으로
                    sp.edit().putLong(K_OPERATION_ID, op.id != null ? op.id : -1L).apply();
                    Intent i = new Intent(SplashActivity.this, DrivingActivity.class);
                    i.putExtra("operationId", op.id);
                    i.putExtra("vehicleId",  op.vehicleId);
                    i.putExtra("routeId",    op.routeId);
                    i.putExtra("routeName",  op.routeName);
                    startActivity(i);
                    finish();
                } else {
                    // 운행 아님 → 로컬 operationId 제거 후 메인으로
                    sp.edit().remove(K_OPERATION_ID).apply();
                    goMain();
                }
            }
            @Override public void onFailure(Call<ApiService.ActiveOperationResp> call, Throwable t) {
                goLogin();
            }
        });
    }

    private void goLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
