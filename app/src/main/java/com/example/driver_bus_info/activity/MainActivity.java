package com.example.driver_bus_info.activity;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private Button btnLogout;       // 로그아웃 버튼
    private Button btnQuit;         // 회원 탈퇴 버튼
    private Button driveStart;      // 운행 시작 버튼
    private ImageButton ivLogMore;  // 운행 기록 더보기 버튼
    private Button btnEditProfile;  // 회원 정보 수정 버튼

    // 사용자 정보 표시용 뷰
    private TextView tvHello, tvDriverName, tvCompany, tvLastLogin;

    private TokenManager tm;
    private ApiService api;
    private String clientType;

    // 빠른 중복 탭 방지용
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
        clientType = DeviceInfo.getClientType(); // "DRIVER_APP" 등

        // 비로그인 방어
        if (tm.accessToken() == null) {
            goLogin();
            return;
        }

        // 1) JWT에 들어있는 name/username/sub로 먼저 즉시 표시 (UX 부드럽게)
        String nameFromJwt = JwtUtils.getClaim(tm.accessToken(), "name");
        if (nameFromJwt == null) nameFromJwt = JwtUtils.getClaim(tm.accessToken(), "username");
        if (nameFromJwt == null) nameFromJwt = JwtUtils.getClaim(tm.accessToken(), "sub");
        if (nameFromJwt != null && tvDriverName != null) {
            tvDriverName.setText(nameFromJwt + " 기사님");
            if (tvHello != null) tvHello.setText("환영합니다.");
        }

        // 2) 서버 /users/me로 정확히 보강
        loadProfile();

        // 네비게이션 & 액션
        btnLogout.setOnClickListener(new DebouncedClick() {
            @Override public void onDebouncedClick(View v) {
                try { tm.clear(); } catch (Exception ignore) {}
                goLogin();
            }
        });

        btnQuit.setOnClickListener(new DebouncedClick() {
            @Override public void onDebouncedClick(View v) {
                startActivity(new Intent(MainActivity.this, UserQuitActivity.class));
            }
        });

        driveStart.setOnClickListener(new DebouncedClick() {
            @Override public void onDebouncedClick(View v) {
                startActivity(new Intent(MainActivity.this, BusListActivity.class));
            }
        });

        ivLogMore.setOnClickListener(new DebouncedClick() {
            @Override public void onDebouncedClick(View v) {
                startActivity(new Intent(MainActivity.this, BusLogActivity.class));
            }
        });

        btnEditProfile.setOnClickListener(new DebouncedClick() {
            @Override public void onDebouncedClick(View v) {
                startActivity(new Intent(MainActivity.this, AccountEditActivity.class));
            }
        });

        // 백버튼: 종료 확인
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmExit(); }
        });
    }

    /** /users/me 호출 → 서버 lastLoginAt 우선 표시, 없거나 실패 시 JWT 근사치 */
    private void loadProfile() {
        String bearer = (tm.tokenType() != null ? tm.tokenType() : "Bearer") + " " + tm.accessToken();

        api.me(bearer, clientType).enqueue(new Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    if (res.code() == 401) { // 만료 등
                        safeLogoutToLogin();
                    } else {
                        // 서버 실패 → JWT로 근사치 표시 시도
                        setLastLoginFromJwtFallback();
                    }
                    return;
                }

                ApiService.UserResponse u = res.body();

                if (u.username != null && !u.username.isEmpty() && tvDriverName != null) {
                    tvDriverName.setText(u.username + " 기사님");
                }

                if (tvCompany != null) {
                    tvCompany.setText("회사 : " + (u.company == null ? "-" : u.company));
                }

                // ★ 최근 접속: 서버 값 우선, 없으면 JWT
                if (tvLastLogin != null) {
                    String text = null;

                    if (u.lastLoginAt != null && !u.lastLoginAt.isEmpty()) {
                        String kst = formatToKST(u.lastLoginAt);
                        if (kst != null) text = "최근 접속 : " + kst;
                    }

                    if (text == null) {
                        text = buildLastLoginFromJwt();
                    }

                    if (text != null) {
                        tvLastLogin.setText(text);
                        tvLastLogin.setVisibility(View.VISIBLE);
                    } else {
                        tvLastLogin.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                // 네트워크 실패 → JWT로 근사치 표시 시도
                setLastLoginFromJwtFallback();
            }
        });
    }

    /** 서버 lastLoginAt(ISO8601: Z/오프셋/밀리초)을 KST로 변환 */
    private String formatToKST(String iso) {
        String[] patterns = new String[] {
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

    /** JWT의 auth_time(없으면 iat)로 근사치 “최근 접속” 문자열 생성 */
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
