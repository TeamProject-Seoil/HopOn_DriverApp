package com.example.driver_bus_info.activity;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;

import com.example.driver_bus_info.R;

public class MainActivity extends AppCompatActivity {

    private Button btnLogout;       // 로그아웃 버튼
    private Button btnQuit;         // 회원 탈퇴 버튼
    private Button driveStart;      // 운행 시작 버튼
    private ImageButton ivLogMore;  // 운행 기록 더보기 버튼
    private Button btnEditProfile;  // 회원 정보 수정 버튼

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 메인 XML 연결

        // 버튼 연결
        btnLogout = findViewById(R.id.btnLogout);
        btnQuit = findViewById(R.id.btnQuit);
        driveStart = findViewById(R.id.drive_start);
        ivLogMore = findViewById(R.id.ivLogMore);
        btnEditProfile = findViewById(R.id.btnEditProfile); // XML의 회원정보수정 버튼 연결

        // 로그아웃 버튼 → 로그인 화면 이동
        btnLogout.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // 회원 탈퇴 버튼 → user_quit 화면 이동
        btnQuit.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UserQuitActivity.class);
            startActivity(intent);
        });

        // 운행 시작 버튼 → bus_list 화면 이동
        driveStart.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BusListActivity.class);
            startActivity(intent);
        });

        // 운행 기록 더보기 버튼 → activity_bus_log 화면 이동
        ivLogMore.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BusLogActivity.class);
            startActivity(intent);
        });

        // 회원정보 수정 버튼 → activity_account_edit 화면 이동
        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AccountEditActivity.class);
            startActivity(intent);
        });
    }
}
