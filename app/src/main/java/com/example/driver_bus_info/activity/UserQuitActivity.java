package com.example.driver_bus_info.activity;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;

public class UserQuitActivity extends AppCompatActivity {

    private Button buttonQuitSubmit;
    private ImageButton registerButtonBack;
    private Button buttonRegisterBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_quit); // user_quit.xml 연결

        // 버튼 연결
        buttonQuitSubmit = findViewById(R.id.buttonQuitSubmit);
        registerButtonBack = findViewById(R.id.registerButtonBack);
        buttonRegisterBack = findViewById(R.id.buttonRegisterBack);

        // 회원탈퇴 버튼 클릭 → 첫 번째 팝업 열기
        buttonQuitSubmit.setOnClickListener(v -> showQuitPopup());

        // 뒤로가기 버튼 → 메인 화면으로 이동
        if (registerButtonBack != null) {
            registerButtonBack.setOnClickListener(v -> {
                startActivity(new Intent(UserQuitActivity.this, MainActivity.class));
                finish();
            });
        }

        // 또 다른 뒤로가기 버튼 → 메인 화면으로 이동
        if (buttonRegisterBack != null) {
            buttonRegisterBack.setOnClickListener(v -> {
                startActivity(new Intent(UserQuitActivity.this, MainActivity.class));
                finish();
            });
        }
    }

    /** 첫 번째 팝업: 탈퇴 확인 */
    private void showQuitPopup() {
        Dialog dialog = new Dialog(UserQuitActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_user_quit);

        // 팝업 크기 설정
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 버튼 연결
        Button btnCancel = dialog.findViewById(R.id.cancel_button);
        Button btnConfirm = dialog.findViewById(R.id.bthOk);

        // 취소 버튼 → 팝업 닫기
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 확인 버튼 → 두 번째 팝업 열기
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();      // 첫 팝업 닫기
            showQuitCompletePopup(); // 두 번째 팝업 열기
        });

        dialog.show();
    }

    /** 두 번째 팝업: 탈퇴 완료 → 로그인 화면 이동 */
    private void showQuitCompletePopup() {
        Dialog dialog = new Dialog(UserQuitActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_user_quit_com);

        // 팝업 크기 설정
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 버튼 연결
        Button btnConfirm = dialog.findViewById(R.id.bthOk);

        // 확인 버튼 → 로그인 화면으로 이동
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(UserQuitActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // 스택 초기화
            startActivity(intent);
            finish();
        });

        dialog.show();
    }
}
