package com.example.driver_bus_info.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;

public class BusLogActivity extends AppCompatActivity {

    private ImageButton ivBack; // 뒤로가기 버튼

    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_log); // 운행 기록 XML 연결

        // 뒤로가기 버튼 연결
        ivBack = findViewById(R.id.ivBack);

        // 클릭 시 MainActivity로 돌아가기
        ivBack.setOnClickListener(v -> {
            Intent intent = new Intent(BusLogActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // 현재 액티비티 종료
        });
    }
}
