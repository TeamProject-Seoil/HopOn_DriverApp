package com.example.driver_bus_info.start;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.activity.MainActivity;


public class StartLoding extends AppCompatActivity {
    private static final int SPLASH_DELAY = 3000; // 3초 지연

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_loding);
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(StartLoding.this, MainActivity.class);
            startActivity(intent);
            finish(); // 로딩 화면은 종료
        }, SPLASH_DELAY);

    }

}
