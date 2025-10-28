// app/src/main/java/com/example/driver_bus_info/activity/LoginActivity.java
package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;
import com.example.driver_bus_info.util.Nav;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText editTextId, editTextPassword;
    private CheckBox checkboxAutoLogin;
    private Button buttonLogin;
    private ImageButton loginButtonBack;
    private TextView textSignup;
    private Button buttonFindIdPw;

    private TokenManager tm;
    private ApiService api;
    private String deviceId;
    private String clientType;

    private Call<ApiService.AuthResponse> refreshCall;
    private Call<ApiService.AuthResponse> loginCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        editTextId        = findViewById(R.id.editTextId);
        editTextPassword  = findViewById(R.id.editTextPassword);
        checkboxAutoLogin = findViewById(R.id.checkbox_auto_login);
        buttonLogin       = findViewById(R.id.buttonLogin);
        loginButtonBack   = findViewById(R.id.loginButtonBack);
        textSignup        = findViewById(R.id.text_signup);
        buttonFindIdPw    = findViewById(R.id.button_find_idpw);

        findViewById(R.id.btnGoInquiry).setOnClickListener(
                v -> startActivity(new Intent(this, InquiryActivity.class)));

        tm  = TokenManager.get(this);
        api = ApiClient.get(this);

        deviceId = tm.deviceId();
        if (deviceId == null) {
            deviceId = DeviceInfo.getDeviceId(this);
            tm.setDeviceId(deviceId);
        }
        clientType = DeviceInfo.getClientType();

        tryAutoLoginIfPossible();

        loginButtonBack.setOnClickListener(v -> confirmExit());
        textSignup.setOnClickListener(v -> startActivity(new Intent(this, RegisterPicExActivity.class)));
        buttonFindIdPw.setOnClickListener(v -> startActivity(new Intent(this, FindAccountActivity.class)));
        buttonLogin.setOnClickListener(v -> doLogin());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmExit(); }
        });
    }

    private void tryAutoLoginIfPossible() {
        if (!tm.isAutoLogin()) return;
        String savedRefresh = tm.refreshToken();
        if (savedRefresh == null || savedRefresh.isEmpty()) return;

        setUi(false, "자동 로그인 중...");
        refreshCall = api.refresh(savedRefresh, clientType, deviceId);
        refreshCall.enqueue(new Callback<ApiService.AuthResponse>() {
            @Override
            public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                if (res.isSuccessful() && res.body() != null) {
                    ApiService.AuthResponse a = res.body();
                    tm.updateAccess(a.accessToken, a.tokenType);
                    if (a.refreshToken != null && !a.refreshToken.isEmpty()) {
                        tm.saveRefreshOnly(a.refreshToken);
                    }
                    // ★ 로그인(자동) 성공 → 현재 운행 조회 후 라우팅
                    routeToActiveOrMain();
                } else {
                    tm.setAutoLogin(false);
                    tm.clearRefresh();
                    setUi(true, "로그인");
                }
            }
            @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                setUi(true, "로그인");
            }
        });
    }

    private void doLogin() {
        String id = text(editTextId);
        String pw = text(editTextPassword);
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(pw)) {
            toast("아이디/비밀번호를 입력하세요");
            return;
        }
        setUi(false, "로그인 중...");

        ApiService.AuthRequest body = new ApiService.AuthRequest(id, pw, clientType, deviceId);
        loginCall = api.login(body);
        loginCall.enqueue(new Callback<ApiService.AuthResponse>() {
            @Override
            public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                if (res.isSuccessful() && res.body() != null) {
                    ApiService.AuthResponse a = res.body();
                    boolean wantAuto = checkboxAutoLogin.isChecked();
                    tm.setAutoLogin(wantAuto);
                    if (wantAuto) {
                        tm.saveLogin(a.accessToken, a.refreshToken, a.tokenType, a.role);
                    } else {
                        tm.updateAccess(a.accessToken, a.tokenType);
                        tm.clearRefresh();
                    }
                    // ★ 로그인 성공 → 현재 운행 조회 후 라우팅
                    routeToActiveOrMain();
                } else {
                    int code = res.code();
                    if (code == 401) { toast("아이디 또는 비밀번호가 올바르지 않습니다."); }
                    else if (code == 409) { toast("다른 기기에서 로그인 중입니다."); }
                    else if (code == 403) { toast("앱 권한이 없습니다."); }
                    else if (code == 423) { toast("계정이 잠겨있거나 승인 대기 중입니다."); }
                    else { toast("로그인 실패 (" + code + ")"); }
                    setUi(true, "로그인");
                }
            }
            @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                toast("네트워크 오류: " + t.getMessage());
                setUi(true, "로그인");
            }
        });
    }

    /** 현재 운행 조회 후 운행중이면 Driving으로, 아니면 Main으로 */
    private void routeToActiveOrMain() {
        api.getActiveOperation(null, "DRIVER_APP").enqueue(new Callback<ApiService.ActiveOperationResp>() {
            @Override public void onResponse(Call<ApiService.ActiveOperationResp> call,
                                             Response<ApiService.ActiveOperationResp> res) {
                if (res.isSuccessful() && res.body()!=null && "RUNNING".equals(res.body().status)) {
                    ApiService.ActiveOperationResp r = res.body();
                    Nav.goDriving(LoginActivity.this, r.id, r.vehicleId, r.routeId, r.routeName);
                } else {
                    gotoMain();
                }
            }
            @Override public void onFailure(Call<ApiService.ActiveOperationResp> call, Throwable t) {
                gotoMain();
            }
        });
    }

    private void gotoMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("앱 종료")
                .setMessage("앱을 종료하시겠습니까?")
                .setPositiveButton("종료", (d, w) -> { d.dismiss(); finishAffinity(); })
                .setNegativeButton("취소", (d, w) -> d.dismiss())
                .show();
    }

    private void setUi(boolean enabled, String btnText) {
        editTextId.setEnabled(enabled);
        editTextPassword.setEnabled(enabled);
        checkboxAutoLogin.setEnabled(enabled);
        buttonLogin.setEnabled(enabled);
        loginButtonBack.setEnabled(enabled);
        textSignup.setEnabled(enabled);
        if (buttonFindIdPw != null) buttonFindIdPw.setEnabled(enabled);
        buttonLogin.setText(btnText);
    }

    private String text(EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override protected void onDestroy() {
        if (refreshCall != null) refreshCall.cancel();
        if (loginCall != null)   loginCall.cancel();
        super.onDestroy();
    }
}
