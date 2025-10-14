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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    // 뷰
    private EditText editTextId, editTextPassword;
    private CheckBox checkboxAutoLogin;
    private Button buttonLogin;
    private ImageButton loginButtonBack;
    private TextView textSignup;
    private Button buttonFindIdPw; // ★ ID/PW 찾기

    // 인증 도구 & 서버 API
    private TokenManager tm;
    private ApiService api;

    // 기기 식별 & 앱 종류(클라이언트 타입)
    private String deviceId;   // ANDROID_ID 사용
    private String clientType; // "DRIVER_APP" | "USER_APP" | "ADMIN_APP"

    // 진행 중 콜 참조 (화면 종료 시 취소용)
    private Call<ApiService.AuthResponse> refreshCall;
    private Call<ApiService.AuthResponse> loginCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // ----- 뷰 바인딩 -----
        editTextId        = findViewById(R.id.editTextId);
        editTextPassword  = findViewById(R.id.editTextPassword);
        checkboxAutoLogin = findViewById(R.id.checkbox_auto_login);
        buttonLogin       = findViewById(R.id.buttonLogin);
        loginButtonBack   = findViewById(R.id.loginButtonBack);
        textSignup        = findViewById(R.id.text_signup);
        buttonFindIdPw    = findViewById(R.id.button_find_idpw);

        // ----- 토큰/클라이언트 초기화 -----
        tm  = TokenManager.get(this);
        api = ApiClient.get(this); // 인터셉터/Authenticator 내장

        // 기기 고유값(ANDROID_ID) 확보 및 보관 (최초 1회 저장)
        deviceId = tm.deviceId();
        if (deviceId == null) {
            deviceId = DeviceInfo.getDeviceId(this);
            tm.setDeviceId(deviceId);
        }

        // 이 빌드의 앱 성격(서버 aud 매칭)
        clientType = DeviceInfo.getClientType(); // 예: "DRIVER_APP"

        // ----- 자동 로그인 시도 (플래그 + refresh 둘 다 만족 시) -----
        tryAutoLoginIfPossible();

        // ----- UI 이벤트 -----
        // 상단 뒤로가기: 종료 확인 다이얼로그
        loginButtonBack.setOnClickListener(v -> confirmExit());

        // 회원가입 첫 화면(가이드)로 (요청한 플로우: RegisterPicEx → Pic → Check → Register → Com)
        textSignup.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterPicExActivity.class)));

        // ★ ID/PW 찾기 화면으로
        buttonFindIdPw.setOnClickListener(v ->
                startActivity(new Intent(this, FindAccountActivity.class)));

        // 로그인 요청
        buttonLogin.setOnClickListener(v -> doLogin());

        // 백 버튼: 종료 확인 (onBackPressed 대체)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmExit(); }
        });
    }

    /** 저장된 refresh 토큰으로 자동 로그인 시도 */
    private void tryAutoLoginIfPossible() {
        // 자동로그인 옵션이 켜져 있고 + refresh 토큰이 있어야만 시도
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
                    // access 갱신
                    tm.updateAccess(a.accessToken, a.tokenType);

                    // 서버가 refresh 재발급했다면 덮어쓰기
                    if (a.refreshToken != null && !a.refreshToken.isEmpty()) {
                        tm.saveRefreshOnly(a.refreshToken);
                    }
                    gotoMain();
                } else {
                    // 실패: 자동로그인 해제 + refresh 제거
                    tm.setAutoLogin(false);
                    tm.clearRefresh();
                    setUi(true, "로그인");
                }
            }
            @Override
            public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                setUi(true, "로그인");
            }
        });
    }

    /** ID/PW 로그인 처리 */
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
                        // 자동로그인 ON: access/refresh 모두 저장
                        tm.saveLogin(a.accessToken, a.refreshToken, a.tokenType, a.role);
                    } else {
                        // 자동로그인 OFF: access만 저장, refresh는 즉시 제거
                        tm.updateAccess(a.accessToken, a.tokenType);
                        tm.clearRefresh();
                    }

                    gotoMain();
                } else {
                    int code = res.code();
                    if (code == 401) {
                        toast("아이디 또는 비밀번호가 올바르지 않습니다.");
                    } else if (code == 409) {
                        toast("다른 기기에서 로그인 중입니다.");
                    } else if (code == 403) {
                        toast("앱 권한이 없습니다.");
                    } else if (code == 423) {
                        toast("계정이 잠겨있거나 승인 대기 중입니다.");
                    } else {
                        toast("로그인 실패 (" + code + ")");
                    }
                    setUi(true, "로그인");
                }
            }
            @Override
            public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                toast("네트워크 오류: " + t.getMessage());
                setUi(true, "로그인");
            }
        });
    }

    /** 로그인 성공 후 메인으로 이동하고 현재 액티비티 종료 */
    private void gotoMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /** 종료 확인 다이얼로그 */
    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("앱 종료")
                .setMessage("앱을 종료하시겠습니까?")
                .setPositiveButton("종료", (d, w) -> {
                    d.dismiss();
                    finishAffinity();
                })
                .setNegativeButton("취소", (d, w) -> d.dismiss())
                .show();
    }

    /** 로그인 진행 중 UI 잠금/해제 및 버튼 텍스트 변경 */
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

    // EditText null-safe 텍스트 추출
    private String text(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    // 짧은 토스트
    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (refreshCall != null) refreshCall.cancel();
        if (loginCall != null) loginCall.cancel();
        super.onDestroy();
    }
}
