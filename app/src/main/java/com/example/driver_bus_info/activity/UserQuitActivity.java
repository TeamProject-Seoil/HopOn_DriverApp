package com.example.driver_bus_info.activity;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserQuitActivity extends AppCompatActivity {

    private Button buttonQuitSubmit;
    private ImageButton registerButtonBack;
    private Button buttonRegisterBack;
    private EditText editPw;
    private CheckBox checkAgree;

    private TokenManager tm;
    private ApiService api;
    private String clientType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_quit);

        // 뷰 바인딩
        buttonQuitSubmit   = findViewById(R.id.buttonQuitSubmit);
        registerButtonBack = findViewById(R.id.registerButtonBack);
        buttonRegisterBack = findViewById(R.id.buttonRegisterBack);
        editPw             = findViewById(R.id.editTextPwConfirm);
        checkAgree         = findViewById(R.id.checkAgree);

        // Core
        tm = TokenManager.get(this);
        api = ApiClient.get(this);
        clientType = DeviceInfo.getClientType();

        // 체크박스 상태에 따라 탈퇴 버튼 활성화
        setQuitEnabled(checkAgree.isChecked());
        checkAgree.setOnCheckedChangeListener((buttonView, isChecked) -> setQuitEnabled(isChecked));

        // 탈퇴 버튼
        buttonQuitSubmit.setOnClickListener(v -> {
            if (!checkAgree.isChecked()) { toast("탈퇴 안내를 모두 확인해 주세요."); return; }
            String pw = safe(editPw.getText());
            if (pw.isEmpty()) { toast("비밀번호를 입력해 주세요."); return; }
            showQuitConfirmPopup(pw);
        });

        // 뒤로가기 → 메인
        if (registerButtonBack != null) {
            registerButtonBack.setOnClickListener(v -> goMain());
        }
        if (buttonRegisterBack != null) {
            buttonRegisterBack.setOnClickListener(v -> goMain());
        }
    }

    private void setQuitEnabled(boolean enabled) {
        buttonQuitSubmit.setEnabled(enabled);
        buttonQuitSubmit.setAlpha(enabled ? 1f : 0.5f);
    }

    /** 탈퇴 확인 1단계 팝업 (확인 시 API 호출) */
    private void showQuitConfirmPopup(String password) {
        Dialog dialog = new Dialog(UserQuitActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_user_quit);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnCancel  = dialog.findViewById(R.id.cancel_button);
        Button btnConfirm = dialog.findViewById(R.id.bthOk);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            requestDeleteAccount(password);
        });

        dialog.show();
    }

    /** 실제 회원 탈퇴 API 호출 */
    private void requestDeleteAccount(String password) {
        // 중복 클릭 방지
        setQuitEnabled(false);

        String bearer = (tm.tokenType() == null ? "Bearer" : tm.tokenType()) + " " + tm.accessToken();
        ApiService.DeleteAccountRequest body = new ApiService.DeleteAccountRequest(password);

        api.deleteMe(bearer, clientType, body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                setQuitEnabled(true);

                if (res.isSuccessful()) {
                    try { tm.clear(); } catch (Exception ignore) {}
                    showQuitCompletePopup();
                } else if (res.code() == 400) {
                    toast("비밀번호가 올바르지 않습니다.");
                } else if (res.code() == 401) {
                    toast("세션이 만료되었습니다. 다시 로그인해 주세요.");
                    goLogin();
                } else {
                    toast("탈퇴 실패 (" + res.code() + ")");
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                setQuitEnabled(true);
                toast("네트워크 오류: " + t.getMessage());
            }
        });
    }

    /** 탈퇴 완료 2단계 팝업 → 로그인 화면으로 이동 */
    private void showQuitCompletePopup() {
        Dialog dialog = new Dialog(UserQuitActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_user_quit_com);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnConfirm = dialog.findViewById(R.id.bthOk);
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            goLogin();
        });

        dialog.show();
    }

    private void goMain() {
        startActivity(new Intent(UserQuitActivity.this, MainActivity.class));
        finish();
    }

    private void goLogin() {
        Intent intent = new Intent(UserQuitActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private String safe(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
}
