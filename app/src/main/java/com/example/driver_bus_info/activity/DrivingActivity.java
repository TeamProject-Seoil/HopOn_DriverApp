package com.example.driver_bus_info.activity;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout; // 🔹 LinearLayout으로 변경됨

import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;

public class DrivingActivity extends AppCompatActivity {

    private Button btnDelay;                // 지연 버튼
    private boolean isDelayActive = false;  // 지연 버튼 상태

    private Button btnDriveEnd;             // 운행 종료 버튼

    // 기존에는 TextView였던 tvHeader → LinearLayout으로 변경
    private LinearLayout headerLayout;      // 상단 헤더 (색상 변경용)
    // private ImageView imgBusLine;        // 버스 라인 이미지 (현재 미사용)

    private Handler handler = new Handler(); // 시뮬레이션용 핸들러
    private boolean isStopSignalActive = false; // 정차 신호 상태

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driving); // driving.xml 연결

        // UI 연결
        btnDelay = findViewById(R.id.btnDelay);
        btnDriveEnd = findViewById(R.id.btnDriveEnd);
        headerLayout = findViewById(R.id.tvHeader); // ⚠️ 이제 LinearLayout로 연결됨
        // imgBusLine = findViewById(R.id.imgBusLine); // ❌ 현재 레이아웃에서 제거됨

        // 지연 버튼 클릭 → 색상 토글
        btnDelay.setOnClickListener(v -> {
            if (isDelayActive) {
                // 🔹 기존 상태로 복원
                btnDelay.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#CACACA")));
            } else {
                // 🔹 지연 상태 색상 (주황색)
                btnDelay.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF7800")));
            }
            isDelayActive = !isDelayActive; // 상태 반전
        });

        // 운행 종료 버튼 → 팝업 띄우기
        btnDriveEnd.setOnClickListener(v -> showDriveEndPopup());

        // 앱 실행 후 5초 뒤 정차 신호 ON → 다시 5초 뒤 OFF (DB 연동할때 삭제해주세요)
        // 🔹 테스트용 시뮬레이션 (실제 DB 연동 시엔 제거)
        handler.postDelayed(this::simulateStopSignalOn, 5000);
    }

    /**
     * 운행 종료 팝업
     */
    private void showDriveEndPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_drive_end);

        // 팝업 크기와 배경 설정
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 팝업 내부 버튼 연결
        Button btnCancel = dialog.findViewById(R.id.cancel_button);
        Button btnOk = dialog.findViewById(R.id.bthOk);

        // 취소 버튼 → 팝업 닫기
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 확인 버튼 → 메인 화면(MainActivity)으로 이동
        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(DrivingActivity.this, MainActivity.class);
            // 뒤로가기 시 DrivingActivity로 돌아오지 않도록 스택 초기화
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        dialog.show(); // 팝업 표시
    }

    /**
     * 정차 신호 수신 시 → UI 변경
     */
    private void simulateStopSignalOn() {
        isStopSignalActive = true;

        // 🔹 LinearLayout 배경 빨간색으로 변경 (이전 tvHeader.setBackgroundColor() 대체)
        headerLayout.setBackgroundColor(Color.parseColor("#FF4B4E"));

        // 버스 라인 이미지를 정차 상태 이미지로 변경
        // imgBusLine.setImageResource(R.drawable.bus_line_stop); // ❌ 현재 사용 안 함

        // 5초 후 신호 OFF
        handler.postDelayed(this::simulateStopSignalOff, 5000);
    }

    /**
     * 정차 신호 해제 시 → UI 복구
     */
    private void simulateStopSignalOff() {
        isStopSignalActive = false;

        // 🔹 LinearLayout 배경 원래 색상으로 복원
        headerLayout.setBackgroundColor(Color.parseColor("#4B93FF"));

        // 버스 라인 이미지를 원래 이미지로 복원
        // imgBusLine.setImageResource(R.drawable.bus_line); // ❌ 현재 사용 안 함
    }

    // DB연결할때 정차 신호 발생 시 simulateStopSignalOn(); 호출하시면 되고
    // 정차 신호 회수할때 simulateStopSignalOff(); 호출하시면 아마도 될겁니다

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 액티비티 종료 시 핸들러 콜백 제거
        handler.removeCallbacksAndMessages(null);
    }
}
