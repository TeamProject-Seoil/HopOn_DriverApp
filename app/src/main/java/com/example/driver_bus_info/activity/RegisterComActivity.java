package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;

public class RegisterComActivity extends AppCompatActivity {

    private Button loginMove;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_com);

        // 버튼 연결
        loginMove = findViewById(R.id.loginMove);

        // 버튼 클릭 시 로그인 화면으로 이동
        loginMove.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterComActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // 현재 액티비티 종료 → 뒤로 가기 눌러도 다시 오지 않음
        });
    }
}
