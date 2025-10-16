package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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

    private EditText editTextId, editTextPw, editTextPwConfirm,
            editTextName, editTextCompany, editTextEmail, editTextPhone, codeInput;
    private TextView textEmailTimer;

    // 비밀번호 규칙/일치 표시 (레이아웃의 ID와 일치: tvRuleLen/Mix/Seq, tvPwMatch)
    private TextView tvRuleLen, tvRuleMix, tvRuleSeq, tvPwMatch;

    private final String[] domains = new String[] {
            "gmail.com","naver.com","daum.net","kakao.com","outlook.com","icloud.com","직접입력"
    };

    // 색상
    private static final int COLOR_OK   = 0xFF2E7D32; // 녹색
    private static final int COLOR_FAIL = 0xFFB00020; // 붉은색
    private static final int COLOR_WEAK = 0xFF666666; // 회색

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register); // 레이아웃 파일명 확인

        bindViews();
        setupEmailSpinner();
        setupClicks();
        handleSystemBack();

        // 포맷터/워처
        wirePwWatchers();     // 비번 규칙 + 일치 표시
        wirePhoneFormatter(); // 전화번호 자동 하이픈
        wireFormEnablers();   // 입력 변화 시 회원가입 버튼 활성화 갱신

        // 초기 상태 반영
        updatePwRules(safeText(editTextPw));
        updatePwMatch();
        updateSubmitEnabled();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.registerButtonBack);
        btnSubmitRegister = findViewById(R.id.buttonSubmitRegister);
        btnSubmitRegister.setEnabled(false);

        spinnerEmailDomain   = findViewById(R.id.spinner_email_domain);
        customDomainWrapper  = findViewById(R.id.custom_domain_wrapper);
        editTextDomainCustom = findViewById(R.id.editTextDomainCustom);
        btnBackToSpinner     = findViewById(R.id.btn_back_to_spinner);

        editTextId        = findViewById(R.id.editTextId);
        editTextPw        = findViewById(R.id.editTextPw);
        editTextPwConfirm = findViewById(R.id.editTextPwConfirm);
        editTextName      = findViewById(R.id.editTextName);
        editTextCompany   = findViewById(R.id.editTextCompany);
        editTextEmail     = findViewById(R.id.editTextEmail);
        editTextPhone     = findViewById(R.id.editTextPhone);
        codeInput         = findViewById(R.id.code_input);

        textEmailTimer = findViewById(R.id.textEmailTimer);

        btnSendCode   = findViewById(R.id.btn_send_code);
        btnVerifyCode = findViewById(R.id.btn_verify_code);
        btnCheckId    = findViewById(R.id.buttonCheckId);

        // 규칙/일치 TextView (ID 일치)
        tvRuleLen = findViewById(R.id.tvRuleLen);
        tvRuleMix = findViewById(R.id.tvRuleMix);
        tvRuleSeq = findViewById(R.id.tvRuleSeq);
        tvPwMatch = findViewById(R.id.tvPwMatch);

        // 초기 규칙 상태
        setRule(tvRuleLen, false, "길이 10~16자");
        setRule(tvRuleMix, false, "영문 대/소문자/숫자 중 2종류 이상, 영문/숫자만 사용");
        setRule(tvRuleSeq, false, "연속 문자/숫자·키보드 시퀀스 3자리 이상 금지");
        tvPwMatch.setText("비밀번호 일치 여부");
        tvPwMatch.setTextColor(COLOR_WEAK);
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
                    spinnerEmailDomain.setVisibility(View.VISIBLE);
                }
                updateSubmitEnabled();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        btnBackToSpinner.setOnClickListener(v -> {
            customDomainWrapper.setVisibility(View.GONE);
            spinnerEmailDomain.setVisibility(View.VISIBLE);
            spinnerEmailDomain.setSelection(0);
            editTextDomainCustom.setText(null);
            updateSubmitEnabled();
        });
    }

    private void setupClicks() {
        // 회원가입 버튼 → 다음 화면
        btnSubmitRegister.setOnClickListener(v -> {
            if (!validateInputs()) return;

            String fullEmail = composeEmail();
            Toast.makeText(this, "Email: " + fullEmail, Toast.LENGTH_SHORT).show();

            // TODO: 서버 전송 로직 연결
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

    /** 폼 유효성 (백엔드 정책과 동일하게 체크) — 드라이버앱 기준 회사명 필수 */
    private boolean validateInputs() {
        String id      = safeText(editTextId).trim();
        String name    = safeText(editTextName).trim();
        String company = safeText(editTextCompany).trim();
        String pw      = safeText(editTextPw);
        String pwc     = safeText(editTextPwConfirm);
        String phone   = safeText(editTextPhone).trim();

        if (id.isEmpty())      { toast("아이디를 입력하세요"); return false; }
        if (name.isEmpty())    { toast("이름을 입력하세요"); return false; }
        if (company.isEmpty()) { toast("회사명을 입력하세요"); return false; }

        if (!isValidPassword(pw)) { toast("비밀번호 규칙을 확인하세요 (10~16자, 영문/숫자, 연속/키보드열 금지)"); return false; }
        if (!pw.equals(pwc))      { toast("비밀번호가 일치하지 않습니다"); return false; }

        String email = composeEmail();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { toast("이메일 형식을 확인하세요"); return false; }
        if (phone.isEmpty()) { toast("전화번호를 입력하세요"); return false; }
        return true;
    }

    // ── 비밀번호 규칙 + 일치 여부 실시간 반영 ──────────────────────────────────
    private void wirePwWatchers() {
        SimpleTextWatcher pwWatcher = new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePwRules(safeText(editTextPw));
                updatePwMatch();
                updateSubmitEnabled();
            }
        };
        editTextPw.addTextChangedListener(pwWatcher);
        editTextPwConfirm.addTextChangedListener(pwWatcher);
    }

    /** 규칙 3개 UI 갱신 */
    private void updatePwRules(String pw) {
        if (TextUtils.isEmpty(pw)) {
            setRule(tvRuleLen, false, "길이 10~16자");
            setRule(tvRuleMix, false, "영문 대/소문자/숫자 중 2종류 이상, 영문/숫자만 사용");
            setRule(tvRuleSeq, false, "연속 문자/숫자·키보드 시퀀스 3자리 이상 금지");
            return;
        }

        // 1) 길이/문자군: 10~16, 영문/숫자만
        boolean lenOk = pw.length() >= 10 && pw.length() <= 16 && pw.matches("^[A-Za-z0-9]+$");
        setRule(tvRuleLen, lenOk, "길이 10~16자");

        // 2) 2가지 이상 조합(대/소/숫자)
        boolean hasUpper=false, hasLower=false, hasDigit=false;
        for (int i=0;i<pw.length();i++) {
            char c = pw.charAt(i);
            if (c>='A'&&c<='Z') hasUpper=true;
            else if (c>='a'&&c<='z') hasLower=true;
            else if (c>='0'&&c<='9') hasDigit=true;
        }
        int classes = (hasUpper?1:0)+(hasLower?1:0)+(hasDigit?1:0);
        setRule(tvRuleMix, classes >= 2, "영문 대/소문자/숫자 중 2종류 이상, 영문/숫자만 사용");

        // 3) 연속/키보드 시퀀스 금지
        boolean badSeq = hasSequentialAlphaOrDigit(pw) || hasKeyboardSequence(pw);
        setRule(tvRuleSeq, !badSeq, "연속 문자/숫자·키보드 시퀀스 3자리 이상 금지");
    }

    private void updatePwMatch() {
        String a = safeText(editTextPw);
        String b = safeText(editTextPwConfirm);

        if (TextUtils.isEmpty(a) && TextUtils.isEmpty(b)) {
            tvPwMatch.setText("비밀번호 일치 여부");
            tvPwMatch.setTextColor(COLOR_WEAK);
            return;
        }
        if (!TextUtils.isEmpty(b) && a.equals(b)) {
            tvPwMatch.setText("✓ 비밀번호가 일치합니다");
            tvPwMatch.setTextColor(COLOR_OK);
        } else {
            tvPwMatch.setText("✗ 비밀번호가 일치하지 않습니다");
            tvPwMatch.setTextColor(COLOR_FAIL);
        }
    }

    private void setRule(TextView tv, boolean ok, String label) {
        tv.setText((ok ? "✓ " : "✗ ") + label);
        tv.setTextColor(ok ? COLOR_OK : COLOR_FAIL);
    }

    // ── 전화번호 자동 하이픈 ───────────────────────────────────────────────────
    private void wirePhoneFormatter() {
        editTextPhone.addTextChangedListener(new SimpleTextWatcher() {
            private boolean isFormatting;
            @Override public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String digits = s.toString().replaceAll("[^0-9]", "");
                String formatted = formatPhone(digits);

                editTextPhone.setText(formatted);
                editTextPhone.setSelection(editTextPhone.getText().length());
                isFormatting = false;
                updateSubmitEnabled();
            }
        });
    }

    /** 한국 전화번호 포맷 */
    private String formatPhone(String d) {
        if (TextUtils.isEmpty(d)) return "";
        // 대표번호 15xx/16xx/18xx-xxxx
        if (d.matches("^(15|16|18)\\d{6,8}$")) {
            if (d.length() >= 8) {
                return d.substring(0,4) + "-" + d.substring(4, Math.min(8, d.length()));
            }
            return d;
        }
        // 02-xxx-xxxx 또는 02-xxxx-xxxx
        if (d.startsWith("02")) {
            if (d.length() <= 2) return d;
            if (d.length() <= 5) return d.substring(0,2) + "-" + d.substring(2);
            if (d.length() <= 9)  return d.substring(0,2) + "-" + d.substring(2,5) + "-" + d.substring(5);
            return d.substring(0,2) + "-" + d.substring(2,6) + "-" + d.substring(6, Math.min(10, d.length()));
        }
        // 휴대폰/일반지역: 3-3-4 또는 3-4-4
        if (d.length() <= 3) return d;
        if (d.length() <= 7)  return d.substring(0,3) + "-" + d.substring(3);
        if (d.length() <= 10) return d.substring(0,3) + "-" + d.substring(3,6) + "-" + d.substring(6);
        return d.substring(0,3) + "-" + d.substring(3,7) + "-" + d.substring(7, Math.min(11, d.length()));
    }

    // ── 비밀번호 유효성(백엔드 정책과 동일) ─────────────────────────────────────
    private boolean isValidPassword(String s) {
        if (TextUtils.isEmpty(s)) return false;
        int len = s.length();
        if (len < 10 || len > 16) return false;

        // 영문/숫자만
        if (!s.matches("^[A-Za-z0-9]+$")) return false;

        boolean hasUpper=false, hasLower=false, hasDigit=false;
        for (int i=0;i<len;i++) {
            char c = s.charAt(i);
            if (c>='A'&&c<='Z') hasUpper=true;
            else if (c>='a'&&c<='z') hasLower=true;
            else if (c>='0'&&c<='9') hasDigit=true;
        }
        int classes = (hasUpper?1:0) + (hasLower?1:0) + (hasDigit?1:0);
        if (classes < 2) return false;

        if (hasSequentialAlphaOrDigit(s)) return false;
        if (hasKeyboardSequence(s)) return false;

        return true;
    }

    private boolean hasSequentialAlphaOrDigit(String s) {
        if (s == null || s.length() < 3) return false;
        for (int i = 0; i <= s.length() - 3; i++) {
            char a = s.charAt(i), b = s.charAt(i+1), c = s.charAt(i+2);
            if (Character.isDigit(a) && Character.isDigit(b) && Character.isDigit(c)) {
                int x=a, y=b, z=c;
                if ((y==x+1 && z==y+1) || (y==x-1 && z==y-1)) return true;
            }
            if (Character.isLetter(a) && Character.isLetter(b) && Character.isLetter(c)) {
                int x=Character.toLowerCase(a), y=Character.toLowerCase(b), z=Character.toLowerCase(c);
                if ((y==x+1 && z==y+1) || (y==x-1 && z==y-1)) return true;
            }
        }
        return false;
    }

    private boolean hasKeyboardSequence(String s) {
        if (s == null || s.length() < 3) return false;
        String lower = s.toLowerCase();
        String[] rows = new String[]{
                "qwertyuiop",
                "asdfghjkl",
                "zxcvbnm",
                "1234567890",
                "0987654321"
        };
        for (String row : rows) {
            for (int i=0; i<=row.length()-3; i++) {
                String sub = row.substring(i, i+3);
                if (lower.contains(sub)) return true;
            }
            String rev = new StringBuilder(row).reverse().toString();
            for (int i=0; i<=rev.length()-3; i++) {
                String sub = rev.substring(i, i+3);
                if (lower.contains(sub)) return true;
            }
        }
        return false;
    }

    // ── 폼 활성화 상태 갱신 (UX) ──────────────────────────────────────────────
    private void wireFormEnablers() {
        SimpleTextWatcher enabler = new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                updateSubmitEnabled();
            }
        };
        editTextId.addTextChangedListener(enabler);
        editTextName.addTextChangedListener(enabler);
        editTextCompany.addTextChangedListener(enabler);
        editTextEmail.addTextChangedListener(enabler);
        editTextDomainCustom.addTextChangedListener(enabler);
        editTextPhone.addTextChangedListener(enabler);
    }

    private void updateSubmitEnabled() {
        String id      = safeText(editTextId).trim();
        String name    = safeText(editTextName).trim();
        String company = safeText(editTextCompany).trim();
        String pw      = safeText(editTextPw);
        String pwc     = safeText(editTextPwConfirm);
        String phone   = safeText(editTextPhone).trim();

        boolean baseOk = !id.isEmpty() && !name.isEmpty() && !company.isEmpty() && !phone.isEmpty();
        boolean pwOk   = isValidPassword(pw) && pw.equals(pwc);

        String email = composeEmail();
        boolean emailOk = !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();

        btnSubmitRegister.setEnabled(baseOk && pwOk && emailOk);
    }

    // ── 유틸 ───────────────────────────────────────────────────────────────────
    private String safeText(EditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
