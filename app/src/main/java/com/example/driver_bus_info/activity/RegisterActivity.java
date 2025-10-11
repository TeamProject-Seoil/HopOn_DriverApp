package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.google.android.material.button.MaterialButton;

public class RegisterActivity extends AppCompatActivity {

    private ImageButton btnBack, btnBackToSpinner;
    private MaterialButton btnSubmitRegister, btnSendCode, btnVerifyCode, btnCheckId;
    private Spinner spinnerEmailDomain;
    private LinearLayout customDomainWrapper;
    private EditText editTextDomainCustom;
    private EditText editTextId, editTextPw, editTextPwConfirm, editTextName, editTextEmail, editTextPhone, codeInput;
    private TextView textEmailTimer;

    private final String[] domains = new String[] {
            "gmail.com","naver.com","daum.net","kakao.com","outlook.com","icloud.com","직접입력"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        bindViews();
        setupEmailSpinner();
        setupClicks();
        handleSystemBack();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.registerButtonBack);
        btnSubmitRegister = findViewById(R.id.buttonSubmitRegister);

        spinnerEmailDomain = findViewById(R.id.spinner_email_domain);
        customDomainWrapper = findViewById(R.id.custom_domain_wrapper);
        editTextDomainCustom = findViewById(R.id.editTextDomainCustom);
        btnBackToSpinner = findViewById(R.id.btn_back_to_spinner);

        editTextId = findViewById(R.id.editTextId);
        editTextPw = findViewById(R.id.editTextPw);
        editTextPwConfirm = findViewById(R.id.editTextPwConfirm);
        editTextName = findViewById(R.id.editTextName);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPhone = findViewById(R.id.editTextPhone);
        codeInput = findViewById(R.id.code_input);

        textEmailTimer = findViewById(R.id.textEmailTimer);

        btnSendCode = findViewById(R.id.btn_send_code);
        btnVerifyCode = findViewById(R.id.btn_verify_code);
        btnCheckId = findViewById(R.id.buttonCheckId);
    }

    private void setupEmailSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                domains
        );
        spinnerEmailDomain.setAdapter(adapter);
        spinnerEmailDomain.setSelection(0);

        spinnerEmailDomain.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = domains[position];
                if ("직접입력".equals(selected)) {
                    spinnerEmailDomain.setVisibility(View.GONE);
                    customDomainWrapper.setVisibility(View.VISIBLE);
                    editTextDomainCustom.requestFocus();
                } else {
                    customDomainWrapper.setVisibility(View.GONE);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        btnBackToSpinner.setOnClickListener(v -> {
            customDomainWrapper.setVisibility(View.GONE);
            spinnerEmailDomain.setVisibility(View.VISIBLE);
            spinnerEmailDomain.setSelection(0);
            editTextDomainCustom.setText(null);
        });
    }

    private void setupClicks() {
        // 회원가입 버튼 → 다음 화면
        btnSubmitRegister.setOnClickListener(v -> {
            if (!validateInputs()) return;

            String fullEmail = composeEmail();
            Toast.makeText(this, "Email: " + fullEmail, Toast.LENGTH_SHORT).show();

            // TODO: 서버 전송 로직 연결 (필요 DTO 구성)
            Intent intent = new Intent(RegisterActivity.this, RegisterComActivity.class);
            startActivity(intent);
            finish();
        });

        // 뒤로가기 → 로그인
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // (선택) 중복확인 / 코드 발송 / 코드 확인은 추후 API 연결
        btnCheckId.setOnClickListener(v ->
                Toast.makeText(this, "아이디 중복확인 API 연결 예정", Toast.LENGTH_SHORT).show());
        btnSendCode.setOnClickListener(v ->
                Toast.makeText(this, "인증 메일 발송 API 연결 예정", Toast.LENGTH_SHORT).show());
        btnVerifyCode.setOnClickListener(v ->
                Toast.makeText(this, "인증 코드 확인 API 연결 예정", Toast.LENGTH_SHORT).show());
    }

    private void handleSystemBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            }
        });
    }

    /** 로컬파트 + 도메인 결합 */
    private String composeEmail() {
        String local = safeText(editTextEmail).trim();
        String domain;
        if (customDomainWrapper.getVisibility() == View.VISIBLE) {
            domain = safeText(editTextDomainCustom).trim();
        } else {
            domain = (String) spinnerEmailDomain.getSelectedItem();
        }
        return local + "@" + domain;
    }

    /** 폼 유효성 (필요 최소만) */
    private boolean validateInputs() {
        String id = safeText(editTextId).trim();
        String name = safeText(editTextName).trim();
        String pw = safeText(editTextPw);
        String pwc = safeText(editTextPwConfirm);
        String phone = safeText(editTextPhone).trim();

        if (id.isEmpty()) { toast("아이디를 입력하세요"); return false; }
        if (name.isEmpty()) { toast("이름을 입력하세요"); return false; }
        if (!isValidPassword(pw)) { toast("비밀번호 형식을 확인하세요 (영문/숫자 10~16자)"); return false; }
        if (!pw.equals(pwc)) { toast("비밀번호가 일치하지 않습니다"); return false; }
        String email = composeEmail();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { toast("이메일 형식을 확인하세요"); return false; }
        if (phone.isEmpty()) { toast("전화번호를 입력하세요"); return false; }
        return true;
    }

    private boolean isValidPassword(String s) {
        if (TextUtils.isEmpty(s)) return false;
        // 백엔드 정책(영/숫자 10~16자, 특수문자 금지)에 맞춤
        return s.matches("^[A-Za-z0-9]{10,16}$");
    }

    private String safeText(EditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}
