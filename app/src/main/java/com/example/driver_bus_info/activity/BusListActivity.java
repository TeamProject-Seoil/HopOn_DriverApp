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

public class BusListActivity extends AppCompatActivity {

    private ImageButton btnBack;       // 뒤로가기 버튼
    private Button btnNewBus;          // 신규 버스 등록 버튼
    private Button bthDriveStart;      // 운행 시작 버튼

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bus_list); // bus_list.xml 연결

        // 버튼 연결
        btnBack = findViewById(R.id.btn_back);
        btnNewBus = findViewById(R.id.btnNewBus);
        bthDriveStart = findViewById(R.id.bthDriveStart);

        // 뒤로가기 버튼 → MainActivity 이동
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(BusListActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // 신규 버스 등록 → 팝업 띄우기
        btnNewBus.setOnClickListener(v -> showBusRegisterPopup());

        // 운행 시작 → 팝업 띄우기
        bthDriveStart.setOnClickListener(v -> showBusConfirmPopup());
    }

    /**
     신규 버스 등록 팝업
     */
    private void showBusRegisterPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_bus_register); // 팝업 XML 연결

        // 팝업 크기와 배경 설정
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 팝업 내부 버튼
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnRegister = dialog.findViewById(R.id.btn_register);

        // 취소 버튼 → 팝업 닫기
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 등록 버튼 → 팝업 닫기
        btnRegister.setOnClickListener(v -> dialog.dismiss());

        dialog.show(); // 팝업 표시
    }

    /**
     운행 시작 확인 팝업
     */
    private void showBusConfirmPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_bus_confirm); // 팝업 XML 연결

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 팝업 내부 버튼
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnOk = dialog.findViewById(R.id.btn_ok);

        // 취소 버튼 → 팝업 닫기
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 확인 버튼 → driving.xml 화면 이동
        btnOk.setOnClickListener(v -> {
            Intent intent = new Intent(BusListActivity.this, DrivingActivity.class);
            startActivity(intent);
            dialog.dismiss();  // 팝업 닫기
            finish();          // BusListActivity 종료
        });

        dialog.show(); // 팝업 표시
    }
}
