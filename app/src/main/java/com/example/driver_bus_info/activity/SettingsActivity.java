package com.example.driver_bus_info.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;
import com.example.driver_bus_info.util.JwtUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.InputStream;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    // 헤더
    private ImageButton btnBack;

    // 섹션 행
    private LinearLayout rowEditAccount, rowInquiry, rowTerms, rowVersion, rowLogout, rowQuit;

    // 프로필 & 버전
    private ImageView imgProfile;
    private TextView tvName, tvPhone, tvCompany, tvEmail, tvVersionRight;

    // 새로고침
    private SwipeRefreshLayout swipe;

    private TokenManager tm;
    private ApiService api;
    private String clientType;

    private static final String FIXED_VERSION_TEXT = "최신 버전 v1.0.0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // ----- View 바인딩 -----
        swipe          = findViewById(R.id.swipe);
        btnBack        = findViewById(R.id.settings_back_button);

        rowEditAccount = findViewById(R.id.rowEditAccount);
        rowInquiry     = findViewById(R.id.rowInquiry);
        rowTerms       = findViewById(R.id.rowTerms);
        rowVersion     = findViewById(R.id.rowVersion);
        rowLogout      = findViewById(R.id.rowLogout);
        rowQuit        = findViewById(R.id.rowQuit);

        imgProfile     = findViewById(R.id.imgProfile);
        tvName         = findViewById(R.id.tvName);
        tvPhone        = findViewById(R.id.tvPhone);
        tvCompany      = findViewById(R.id.tvCompany);
        tvEmail        = findViewById(R.id.tvEmail);
        tvVersionRight = findViewById(R.id.tvVersionRight);

        tm         = TokenManager.get(this);
        api        = ApiClient.get(this);
        clientType = DeviceInfo.getClientType(); // 예: ANDROID

        // 액세스 토큰 없으면 로그인으로
        if (tm.accessToken() == null || tm.accessToken().isEmpty()) {
            goLogin();
            return;
        }

        // 최초 바인딩
        bindProfileFromJwt();      // JWT로 즉시 표기
        fetchProfileFromServer();  // 서버 값으로 갱신
        tvVersionRight.setText(FIXED_VERSION_TEXT);

        // ----- 이벤트 -----
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (swipe != null) {
            swipe.setOnRefreshListener(() -> {
                bindProfileFromJwt();
                fetchProfileFromServer();
            });
        }

        // 회원정보 수정: 비밀번호 확인 다이얼로그 → 성공 시 AccountEditActivity 이동
        rowEditAccount.setOnClickListener(v -> showVerifyPasswordThenEditDialog());

        rowInquiry.setOnClickListener(v ->
                startActivity(new Intent(this, InquiryActivity.class)));

        rowTerms.setOnClickListener(v ->
                startActivity(new Intent(this, TermsPrivacyActivity.class)));

        rowVersion.setOnClickListener(v ->
                Toast.makeText(this, FIXED_VERSION_TEXT, Toast.LENGTH_SHORT).show());

        // 로그아웃: 커스텀 하단 다이얼로그
        rowLogout.setOnClickListener(v -> showLogoutDialog());

        rowQuit.setOnClickListener(v ->
                startActivity(new Intent(this, UserQuitActivity.class)));
    }

    /** JWT에서 즉시 뿌릴 수 있는 필드 먼저 바인딩 */
    private void bindProfileFromJwt() {
        String at = tm.accessToken();

        String name = JwtUtils.getClaim(at, "name");
        if (name == null) name = JwtUtils.getClaim(at, "username");
        if (name == null) name = JwtUtils.getClaim(at, "sub");
        tvName.setText(name != null && !name.isEmpty() ? name : "알 수 없음");

        String phone = JwtUtils.getClaim(at, "tel");
        tvPhone.setText(phone != null && !phone.isEmpty() ? phone : "전화번호 없음");

        String company = JwtUtils.getClaim(at, "company");
        tvCompany.setText(company != null && !company.isEmpty() ? company : "회사 정보 없음");

        String email = JwtUtils.getClaim(at, "email");
        tvEmail.setText(email != null && !email.isEmpty() ? email : "이메일 없음");

        // 이미지는 기본 플레이스홀더(ic_user_placeholder)를 그대로 사용 (서버에서 있으면 교체)
    }

    /** 서버에서 최신 프로필/이미지 가져와 갱신 */
    private void fetchProfileFromServer() {
        final String bearer = (tm.tokenType() != null ? tm.tokenType() : "Bearer") + " " + tm.accessToken();

        api.me(bearer, clientType).enqueue(new Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                if (swipe != null) swipe.setRefreshing(false);

                if (!res.isSuccessful() || res.body() == null) {
                    if (res.code() == 401) goLogin();
                    return;
                }
                ApiService.UserResponse u = res.body();

                if (u.username != null && !u.username.isEmpty()) tvName.setText(u.username);
                if (u.tel != null && !u.tel.isEmpty())           tvPhone.setText(u.tel);
                if (u.company != null && !u.company.isEmpty())   tvCompany.setText(u.company);
                if (u.email != null && !u.email.isEmpty())       tvEmail.setText(u.email);

                if (imgProfile != null && u.hasProfileImage) {
                    api.meImage(bearer, clientType).enqueue(new Callback<ResponseBody>() {
                        @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> resImg) {
                            if (!resImg.isSuccessful() || resImg.body() == null) return;
                            try (InputStream is = resImg.body().byteStream()) {
                                imgProfile.setImageBitmap(BitmapFactory.decodeStream(is));
                            } catch (Exception ignore) {}
                        }
                        @Override public void onFailure(Call<ResponseBody> call, Throwable t) { }
                    });
                }
            }

            @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                if (swipe != null) swipe.setRefreshing(false);
            }
        });
    }

    /** 회원정보 수정: 비밀번호 확인 → 성공 시 AccountEditActivity로 이동 */
    private void showVerifyPasswordThenEditDialog() {
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
        android.view.View btnCancel = dialog.findViewById(R.id.btn_cancel);
        android.view.View btnOk = dialog.findViewById(R.id.btn_ok);

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
                goLogin();
                return;
            }

            ApiService.AuthRequest req = new ApiService.AuthRequest(userid, pwd, clientType, tm.deviceId());
            ApiClient.get(this).login(req).enqueue(new Callback<ApiService.AuthResponse>() {
                @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                    if (res.isSuccessful() && res.body() != null) {
                        dialog.dismiss();
                        Intent i = new Intent(SettingsActivity.this, AccountEditActivity.class);
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

    /** 로그아웃: 커스텀 하단 다이얼로그 */
    private void showLogoutDialog() {
        final Dialog dialog = new Dialog(this, R.style.CenterDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_logout_center);

        Window window = dialog.getWindow();
        if (window != null) {
            // 배경 투명: 모서리 둥근 카드가 또렷하게 보이도록
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            // 가운데 배치 + 화면 폭은 넓게 잡되, 카드 자체가 320dp라 실제 보여지는 건 좁음
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);

            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.45f; // 뒷배경 어둡게
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(lp);
        }

        android.view.View btnCancel = dialog.findViewById(R.id.btnCancel);
        android.view.View btnLogout = dialog.findViewById(R.id.btnLogout);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnLogout.setOnClickListener(v -> {
            try { tm.clear(); } catch (Exception ignore) {}
            dialog.dismiss();
            goLogin();
        });

        dialog.show();
    }


    private void goLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
