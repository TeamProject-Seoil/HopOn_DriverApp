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

public class AccountEditActivity extends AppCompatActivity {

    private Button btnUpdate; // 업데이트 버튼
    private ImageButton btnBack;       // 뒤로가기 버튼

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit); // XML 연결

        // 버튼 연결
        btnUpdate = findViewById(R.id.btn_update);
        btnBack = findViewById(R.id.account_back_button);

        // 업데이트 버튼 클릭 → 첫 번째 팝업 띄우기
        btnUpdate.setOnClickListener(v -> showUserEditPopup());

        // 뒤로가기 버튼 → MainActivity 이동
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(AccountEditActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    /**
     * 첫 번째 팝업: popup_user_edit
     */
    private void showUserEditPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_user_edit); // 팝업 XML 연결

        // 팝업 크기 조정
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 버튼 연결
        Button btnCancel = dialog.findViewById(R.id.cancel_button);
        Button btnOk = dialog.findViewById(R.id.bthOk);

        // 취소 버튼 → 팝업 닫기
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 확인 버튼 → 첫 번째 팝업 닫고 두 번째 팝업 띄우기
        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            showUserEditComPopup(); // 다음 팝업 실행
        });

        dialog.show(); // 팝업 표시
    }

    /**
     * 두 번째 팝업: popup_user_edit_com
     */
    private void showUserEditComPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_user_edit_com); // 완료 팝업 XML 연결

        // 팝업 크기 조정
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 버튼 연결
        Button btnOk = dialog.findViewById(R.id.bthOk);

        // 확인 버튼 → 팝업 닫고 메인 화면으로 이동
        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            // MainActivity로 이동
            Intent intent = new Intent(AccountEditActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish(); // 현재 화면 종료
        });

        dialog.show(); // 팝업 표시
    }
}
