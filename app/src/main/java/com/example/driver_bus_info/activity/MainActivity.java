package com.example.driver_bus_info.activity;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;
import com.example.driver_bus_info.util.JwtUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private Button btnLogout;
    private Button btnQuit;
    private Button driveStart;
    private ImageButton ivLogMore;
    private Button btnEditProfile;

    private TextView tvHello, tvDriverName, tvCompany, tvLastLogin;

    private TokenManager tm;
    private ApiService api;
    private String clientType;

    // 중복탭 방지용
    private static abstract class DebouncedClick implements View.OnClickListener {
        private static final long GAP = 600L;
        private long last = 0L;
        @Override public final void onClick(View v) {
            long now = System.currentTimeMillis();
            if (now - last < GAP) return;
            last = now;
            onDebouncedClick(v);
        }
        public abstract void onDebouncedClick(View v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 바인딩
        btnLogout      = findViewById(R.id.btnLogout);
        btnQuit        = findViewById(R.id.btnQuit);
        driveStart     = findViewById(R.id.drive_start);
        ivLogMore      = findViewById(R.id.ivLogMore);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        tvHello       = findViewById(R.id.tvHello);
        tvDriverName  = findViewById(R.id.tvDriverName);
        tvCompany     = findViewById(R.id.tvCompany);
        tvLastLogin   = findViewById(R.id.tvLastLogin);

        // Core
        tm         = TokenManager.get(this);
        api        = ApiClient.get(this);
        clientType = DeviceInfo.getClientType();

        // 비로그인 방어
        if (tm.accessToken() == null) {
            goLogin();
            return;
        }

        // JWT에서 이름 표시
        String nameFromJwt = JwtUtils.getClaim(tm.accessToken(), "name");
        if (nameFromJwt == null) nameFromJwt = JwtUtils.getClaim(tm.accessToken(), "username");
        if (nameFromJwt == null) nameFromJwt = JwtUtils.getClaim(tm.accessToken(), "sub");
        if (nameFromJwt != null && tvDriverName != null) {
            tvDriverName.setText(nameFromJwt + " 기사님");
            if (tvHello != null) tvHello.setText("환영합니다.");
        }

        // 서버에서 최신 프로필 불러오기
        loadProfile();

        // 액션
        btnLogout.setOnClickListener(new DebouncedClick() {
            @Override public void onDebouncedClick(View v) {
                try { tm.clear(); } catch (Exception ignore) {}
                goLogin();
            }
        });
        btnQuit.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, UserQuitActivity.class))
        );
        driveStart.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, BusListActivity.class))
        );
        ivLogMore.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, BusLogActivity.class))
        );

        // 회원정보 수정 → 비번 인증 다이얼로그
        btnEditProfile.setOnClickListener(v -> showVerifyPasswordDialog());

        // 백버튼: 종료 확인
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmExit(); }
        });
    }

    // ✅ 메인 복귀 시 회사명, 이름, 이메일 등 최신 정보 다시 불러오기
    @Override
    protected void onResume() {
        super.onResume();
        if (tm != null && tm.accessToken() != null) {
            loadProfile();
        }
    }

    /** /users/me 호출 */
    private void loadProfile() {
        String bearer = (tm.tokenType() != null ? tm.tokenType() : "Bearer") + " " + tm.accessToken();

        api.me(bearer, clientType).enqueue(new Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    if (res.code() == 401) { safeLogoutToLogin(); }
                    else { setLastLoginFromJwtFallback(); }
                    return;
                }
                ApiService.UserResponse u = res.body();

                // 이름
                if (u.username != null && !u.username.isEmpty() && tvDriverName != null) {
                    tvDriverName.setText(u.username + " 기사님");
                }

                // ✅ 회사명 즉시 반영
                if (tvCompany != null) {
                    tvCompany.setText("회사 : " + (u.company == null ? "-" : u.company));
                }

                // 최근 로그인
                if (tvLastLogin != null) {
                    String text = null;
                    if (u.lastLoginAt != null && !u.lastLoginAt.isEmpty()) {
                        String kst = formatToKST(u.lastLoginAt);
                        if (kst != null) text = "최근 접속 : " + kst;
                    }
                    if (text == null) text = buildLastLoginFromJwt();
                    if (text != null) {
                        tvLastLogin.setText(text);
                        tvLastLogin.setVisibility(View.VISIBLE);
                    } else {
                        tvLastLogin.setVisibility(View.GONE);
                    }
                }
            }

            @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                setLastLoginFromJwtFallback();
            }
        });
    }

    private String formatToKST(String iso) {
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat in = new SimpleDateFormat(p, Locale.US);
                if (p.endsWith("'Z'")) in.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = in.parse(iso);
                SimpleDateFormat out = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA);
                out.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
                return out.format(date);
            } catch (ParseException ignore) {}
        }
        return null;
    }

    private String buildLastLoginFromJwt() {
        String at = tm.accessToken();
        if (at == null) return null;
        Long sec = JwtUtils.getLongClaim(at, "auth_time");
        if (sec == null) sec = JwtUtils.getLongClaim(at, "iat");
        if (sec == null) return null;
        Date d = new Date(sec * 1000L);
        SimpleDateFormat out = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA);
        out.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return "최근 접속 : " + out.format(d);
    }

    private void setLastLoginFromJwtFallback() {
        if (tvLastLogin == null) return;
        String text = buildLastLoginFromJwt();
        if (text != null) {
            tvLastLogin.setText(text);
            tvLastLogin.setVisibility(View.VISIBLE);
        } else {
            tvLastLogin.setVisibility(View.GONE);
        }
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("앱 종료")
                .setMessage("앱을 종료하시겠습니까?")
                .setPositiveButton("종료", (d, w) -> { d.dismiss(); finishAffinity(); })
                .setNegativeButton("취소", (d, w) -> d.dismiss())
                .show();
    }

    private void safeLogoutToLogin() {
        try { tm.clear(); } catch (Exception ignore) {}
        goLogin();
    }

    private void goLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------------- 중앙 모달 다이얼로그 ----------------

    private void showVerifyPasswordDialog() {
        final Dialog dialog = new Dialog(this, R.style.CenterDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_verify_password_center);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.4f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(lp);
        }

        TextInputLayout til = dialog.findViewById(R.id.til_password);
        TextInputEditText et = dialog.findViewById(R.id.et_password);
        View btnCancel = dialog.findViewById(R.id.btn_cancel);
        View btnOk = dialog.findViewById(R.id.btn_ok);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnOk.setOnClickListener(v -> {
            String pwd = et.getText() == null ? "" : et.getText().toString();
            if (pwd.isEmpty()) { til.setError("비밀번호를 입력하세요"); return; }
            til.setError(null);
            btnOk.setEnabled(false);
            btnCancel.setEnabled(false);

            String userid = JwtUtils.getClaim(tm.accessToken(), "sub");
            if (userid == null || userid.isEmpty()) {
                toast("사용자 정보를 확인할 수 없습니다. 다시 로그인해 주세요.");
                dialog.dismiss();
                safeLogoutToLogin();
                return;
            }

            ApiService.AuthRequest req = new ApiService.AuthRequest(userid, pwd, clientType, tm.deviceId());
            api.login(req).enqueue(new Callback<ApiService.AuthResponse>() {
                @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                    if (res.isSuccessful() && res.body() != null) {
                        dialog.dismiss();
                        // 비밀번호 전달 후 AccountEditActivity 진입
                        Intent i = new Intent(MainActivity.this, AccountEditActivity.class);
                        i.putExtra("verified_pw", pwd);
                        startActivity(i);
                    } else {
                        til.setError(res.code() == 401 ? "비밀번호가 올바르지 않습니다" : "인증 실패 (" + res.code() + ")");
                        btnOk.setEnabled(true);
                        btnCancel.setEnabled(true);
                    }
                }

                @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                    til.setError("네트워크 오류: " + t.getMessage());
                    btnOk.setEnabled(true);
                    btnCancel.setEnabled(true);
                }
            });
        });

        dialog.show();
    }
}
