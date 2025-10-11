package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.ui.Anim;

/**
 * RegisterCheckActivity
 * - 제출 버튼: RegisterActivity 로 이동 (요청한 흐름)
 * - 상단/하단 뒤로: RegisterPicActivity 로 이동
 * - 애니메이션: Anim 유틸 사용 (없는 id는 안전 무시)
 */
public class RegisterCheckActivity extends AppCompatActivity {

    public static final String EXTRA_LICENSE_PHOTO_URI = "licensePhotoUri";
    public static final String EXTRA_LICENSE_NUMBER    = "licenseNumber";
    public static final String EXTRA_ACQUIRED_DATE     = "acquiredDate";
    public static final String EXTRA_NAME              = "name";
    public static final String EXTRA_BIRTH             = "birth";

    private Button buttonRegisterSubmit;
    private ImageButton registerButtonBack;
    private Button bottomBackButton;

    // 확인 화면 표시용 텍스트 뷰(레이아웃이 TextView이므로 TextView로 선언)
    private TextView tvName, tvBirth, tvLicenseCode, tvLicenseDay;

    private boolean navigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_check);

        buttonRegisterSubmit = findViewById(R.id.buttonRegisterSubmit);
        registerButtonBack   = findViewById(R.id.registerButtonBack);
        bottomBackButton     = findViewById(R.id.buttonRegisterBack);

        // 레이아웃 id에 맞춰 TextView로 바인딩
        tvName        = findViewById(R.id.editTextId);
        tvBirth       = findViewById(R.id.editTextBirth);
        tvLicenseCode = findViewById(R.id.editTextlicenseCode);
        tvLicenseDay  = findViewById(R.id.editTextlicenseDay);

        // 이전 화면(촬영 화면)에서 전달된 값 표시
        Intent fromPic = getIntent();
        if (fromPic != null) {
            setIfNonNull(tvName,        fromPic.getStringExtra(EXTRA_NAME));
            setIfNonNull(tvBirth,       fromPic.getStringExtra(EXTRA_BIRTH));
            setIfNonNull(tvLicenseCode, fromPic.getStringExtra(EXTRA_LICENSE_NUMBER));
            setIfNonNull(tvLicenseDay,  fromPic.getStringExtra(EXTRA_ACQUIRED_DATE));
        }

        // 제출 → RegisterActivity (최종 확인된 값 + 사진 URI 전달)
        if (buttonRegisterSubmit != null) {
            buttonRegisterSubmit.setOnClickListener(v -> {
                if (navigating) return;
                navigating = true;
                Anim.bump(v);

                Intent toRegister = new Intent(this, RegisterActivity.class);

                // 확인 화면에서 확정된 값들
                toRegister.putExtra(EXTRA_NAME,           getTextOrNull(tvName));
                toRegister.putExtra(EXTRA_BIRTH,          getTextOrNull(tvBirth));
                toRegister.putExtra(EXTRA_LICENSE_NUMBER, getTextOrNull(tvLicenseCode));
                toRegister.putExtra(EXTRA_ACQUIRED_DATE,  getTextOrNull(tvLicenseDay));

                // 촬영 화면에서 넘겨온 사진 URI 그대로 전달
                if (fromPic != null && fromPic.hasExtra(EXTRA_LICENSE_PHOTO_URI)) {
                    toRegister.putExtra(
                            EXTRA_LICENSE_PHOTO_URI,
                            fromPic.getStringExtra(EXTRA_LICENSE_PHOTO_URI)
                    );
                }

                startActivity(toRegister);
                Anim.fadeTransition(this);
                finish();
            });
        }

        // 뒤로가기(상단/하단)
        if (bottomBackButton != null) bottomBackButton.setOnClickListener(v -> navigateBackToPic());
        if (registerButtonBack != null) registerButtonBack.setOnClickListener(v -> navigateBackToPic());
    }

    @Override
    public void onBackPressed() {
        navigateBackToPic();
    }

    private void navigateBackToPic() {
        if (navigating) return;
        navigating = true;
        if (bottomBackButton != null) Anim.bump(bottomBackButton);
        else if (registerButtonBack != null) Anim.bump(registerButtonBack);
        startActivity(new Intent(this, RegisterPicActivity.class));
        Anim.fadeTransition(this);
        finish();
    }

    private void setIfNonNull(TextView tv, String v) {
        if (tv != null && v != null) tv.setText(v);
    }

    // ▼ Java 11 호환: 패턴 매칭 대신 전통적인 instanceof + 캐스팅 사용
    private String getTextOrNull(View v) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            CharSequence cs = tv.getText();
            return cs == null ? null : cs.toString().trim();
        }
        return null;
    }
}
