package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.ui.Anim;

public class RegisterPicExActivity extends AppCompatActivity {

    private Button buttonSubmitNext;
    private ImageButton registerButtonBack;

    // 빠른 연타 방지 가드
    private boolean navigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_pic_ex);

        buttonSubmitNext   = findViewById(R.id.buttonSubmitnext);
        registerButtonBack = findViewById(R.id.registerButtonBack);

        // === 등장 애니메이션: 첫 진입 때만 ===
        if (savedInstanceState == null) {
            View topBar = findViewById(R.id.top_bar);
            View title  = findViewById(R.id.textTitle);
            View guide1 = findViewById(R.id.textView7);
            View sample = findViewById(R.id.imageView2);
            View guide2 = findViewById(R.id.textView8);
            View next   = findViewById(R.id.buttonSubmitnext);

            if (topBar != null)  Anim.slideDownIn(topBar, 24f, 0);
            if (title != null)   Anim.slideDownIn(title, 24f, 20);
            if (registerButtonBack != null) Anim.slideDownIn(registerButtonBack, 24f, 20);
            Anim.intro(this,
                    R.id.textView7, R.id.imageView2, R.id.textView8, R.id.buttonSubmitnext);
        }

        // 다음 버튼 → 촬영 가이드
        if (buttonSubmitNext != null) {
            buttonSubmitNext.setOnClickListener(v -> {
                if (navigating) return;
                navigating = true;
                Anim.bump(v);
                startActivity(new Intent(this, RegisterPicActivity.class));
                Anim.fadeTransition(this);
                finish();
            });
        }

        // 상단 뒤로가기 → 회원가입 첫 화면
        if (registerButtonBack != null) {
            registerButtonBack.setOnClickListener(v -> navigateBackToRegister());
        }
    }

    @Override
    public void onBackPressed() {
        // 시스템 뒤로가기도 동일한 트랜지션으로
        navigateBackToRegister();
    }

    private void navigateBackToRegister() {
        if (navigating) return;
        navigating = true;
        if (registerButtonBack != null) Anim.bump(registerButtonBack);
        startActivity(new Intent(this, LoginActivity.class));
        Anim.fadeTransition(this);
        finish();
    }
}
