package com.example.driver_bus_info.activity;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ImageButton ivLogMore, btnEditProfile;
    private ImageButton btnNotice, btnSettings;
    private TextView tvNoticeBadge;

    private ImageView imgDriverProfile;
    private TextView tvHello, tvDriverName, tvCompany, tvLastLogin;

    private TokenManager tm;
    private ApiService api;
    private String clientType;

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

        // top-right
        btnNotice     = findViewById(R.id.btnNotice);
        btnSettings   = findViewById(R.id.btnSettings);
        tvNoticeBadge = findViewById(R.id.tvNoticeBadge);

        ivLogMore        = findViewById(R.id.ivLogMore);
        btnEditProfile   = findViewById(R.id.btnEditProfile);
        imgDriverProfile = findViewById(R.id.imgDriverProfile);

        tvHello       = findViewById(R.id.tvHello);
        tvDriverName  = findViewById(R.id.tvDriverName);
        tvCompany     = findViewById(R.id.tvCompany);
        tvLastLogin   = findViewById(R.id.tvLastLogin);

        tm         = TokenManager.get(this);
        api        = ApiClient.get(this);
        clientType = DeviceInfo.getClientType();

        if (tm.accessToken() == null) {
            goLogin();
            return;
        }

        String nameFromJwt = JwtUtils.getClaim(tm.accessToken(), "name");
        if (nameFromJwt == null) nameFromJwt = JwtUtils.getClaim(tm.accessToken(), "username");
        if (nameFromJwt == null) nameFromJwt = JwtUtils.getClaim(tm.accessToken(), "sub");
        if (nameFromJwt != null) {
            tvDriverName.setText(nameFromJwt + " 기사님");
            tvHello.setText("환영합니다.");
        }

        // Top-right actions
        btnNotice.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, NoticeActivity.class)));
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        // 기타
        ivLogMore.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, BusLogActivity.class)));
        btnEditProfile.setOnClickListener(v -> showVerifyPasswordDialog());

        // 데이터 로드
        loadProfile();
        fetchUnreadNoticeCount(); // 배지 갱신

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmExit(); }
        });

        // 운행 시작 버튼 클릭 시 bus_list 화면으로 이동
        Button btnDriveStart = findViewById(R.id.drive_start);
        btnDriveStart.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BusListActivity.class);
            startActivity(intent);
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tm != null && tm.accessToken() != null) {
            loadProfile();
            fetchUnreadNoticeCount();
        }
    }

    /** 서버에서 미확인 공지 개수 조회 -> 오른쪽 상단 배지 갱신 */
    private void fetchUnreadNoticeCount() {
        if (tm == null || tm.accessToken() == null) {
            updateNoticeBadge(0);
            return;
        }
        final String bearer = (tm.tokenType() != null ? tm.tokenType() : "Bearer") + " " + tm.accessToken();

        api.getUnreadNoticeCount(bearer).enqueue(new Callback<Map<String, Integer>>() {
            @Override public void onResponse(Call<Map<String, Integer>> call, Response<Map<String, Integer>> res) {
                if (!res.isSuccessful() || res.body() == null) { updateNoticeBadge(0); return; }
                Integer c = res.body().get("count");
                if (c == null) c = res.body().get("unreadCount");
                updateNoticeBadge(c == null ? 0 : c);
            }
            @Override public void onFailure(Call<Map<String, Integer>> call, Throwable t) {
                updateNoticeBadge(0);
            }
        });
    }

    /** 배지 표시/숨김 */
    private void updateNoticeBadge(int unreadCount) {
        if (tvNoticeBadge == null) return;
        if (unreadCount > 0) {
            tvNoticeBadge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            tvNoticeBadge.setVisibility(View.VISIBLE);
        } else {
            tvNoticeBadge.setVisibility(View.GONE);
        }
    }

    private void loadProfile() {
        final String bearer = (tm.tokenType() != null ? tm.tokenType() : "Bearer") + " " + tm.accessToken();

        api.me(bearer, clientType).enqueue(new Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    if (res.code() == 401) { safeLogoutToLogin(); }
                    else { setLastLoginFromJwtFallback(); }
                    return;
                }
                ApiService.UserResponse u = res.body();

                if (u.username != null && !u.username.isEmpty()) {
                    tvDriverName.setText(u.username + " 기사님");
                }
                tvCompany.setText("회사 : " + (u.company == null ? "-" : u.company));

                if (tvLastLogin != null) {
                    String iso = pickMostRecentIso(u.lastLoginAtIso, u.lastRefreshAtIso);
                    String text = null;
                    if (iso != null) {
                        String kst = formatToKST(iso);
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

                loadProfileImage(bearer);
            }

            @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                setLastLoginFromJwtFallback();
            }
        });
    }

    private void loadProfileImage(String bearer) {
        api.meImage(bearer, clientType).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                if (!res.isSuccessful() || res.body() == null || imgDriverProfile == null) return;
                try {
                    imgDriverProfile.setImageBitmap(BitmapFactory.decodeStream(res.body().byteStream()));
                } catch (Exception ignore) {}
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) { }
        });
    }

    private String pickMostRecentIso(String a, String b) {
        Long ta = toEpochMillis(a);
        Long tb = toEpochMillis(b);
        if (ta == null && tb == null) return null;
        if (tb == null || (ta != null && ta >= tb)) return a;
        return b;
    }

    private Long toEpochMillis(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat in = new SimpleDateFormat(p, Locale.US);
                if (p.endsWith("'Z'")) in.setTimeZone(TimeZone.getTimeZone("UTC"));
                else if (!p.endsWith("XXX")) in.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
                Date d = in.parse(iso);
                if (d != null) return d.getTime();
            } catch (Exception ignore) {}
        }
        return null;
    }

    private String formatToKST(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        SimpleDateFormat out = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA);
        out.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat in = new SimpleDateFormat(p, Locale.US);
                if (p.endsWith("'Z'")) in.setTimeZone(TimeZone.getTimeZone("UTC"));
                else if (!p.endsWith("XXX")) in.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
                Date d = in.parse(iso);
                if (d != null) return out.format(d);
            } catch (Exception ignore) {}
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
                Toast.makeText(this, "사용자 정보를 확인할 수 없습니다. 다시 로그인해 주세요.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                safeLogoutToLogin();
                return;
            }

            ApiService.AuthRequest req = new ApiService.AuthRequest(userid, pwd, clientType, tm.deviceId());
            ApiClient.get(this).login(req).enqueue(new Callback<ApiService.AuthResponse>() {
                @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                    if (res.isSuccessful() && res.body() != null) {
                        dialog.dismiss();
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
