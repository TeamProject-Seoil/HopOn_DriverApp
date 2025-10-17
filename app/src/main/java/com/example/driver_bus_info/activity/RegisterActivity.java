package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private ImageButton btnBack, btnBackToSpinner;
    private MaterialButton btnSubmitRegister, btnSendCode, btnVerifyCode, btnCheckId;
    private Spinner spinnerEmailDomain;
    private LinearLayout customDomainWrapper;
    private EditText editTextDomainCustom;

    private EditText editTextId, editTextPw, editTextPwConfirm,
            editTextName, editTextCompany, editTextEmail, editTextPhone, codeInput;
    private TextView textEmailTimer;

    // 비밀번호 규칙/일치 표시
    private TextView tvRuleLen, tvRuleMix, tvRuleSeq, tvPwMatch;

    private final String[] domains = new String[] {
            "gmail.com","naver.com","daum.net","kakao.com","outlook.com","icloud.com","직접입력"
    };

    // 색상
    private static final int COLOR_OK   = 0xFF2E7D32; // 녹색
    private static final int COLOR_FAIL = 0xFFB00020; // 붉은색
    private static final int COLOR_WEAK = 0xFF666666; // 회색

    // ===== API & 상태 =====
    private ApiService api;
    private String clientType;  // ex) DRIVER_APP
    private String deviceId;    // ex) DeviceInfo.getOrCreateDeviceId(...)
    private String verificationId;                   // 이메일 인증 요청 id
    private final AtomicBoolean emailVerified = new AtomicBoolean(false);
    private CountDownTimer emailTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Api/환경
        api = ApiClient.get(this);
        clientType = DeviceInfo.getClientType();
        deviceId   = DeviceInfo.getOrCreateDeviceId(this);

        bindViews();

        // ▼▼▼ 여기서 이름 자동 입력 (RegisterCheckActivity에서 전달된 값)
        Intent fromCheckAtCreate = getIntent();
        if (fromCheckAtCreate != null) {
            String nameFromCheck = fromCheckAtCreate.getStringExtra(RegisterCheckActivity.EXTRA_NAME);
            if (!TextUtils.isEmpty(nameFromCheck) && editTextName != null && TextUtils.isEmpty(editTextName.getText())) {
                editTextName.setText(nameFromCheck);
            }
        }
        // ▲▲▲ 끝

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

        // 비밀번호 규칙/일치 텍스트
        tvRuleLen  = findViewById(R.id.tvRuleLen);
        tvRuleMix  = findViewById(R.id.tvRuleMix);
        tvRuleSeq  = findViewById(R.id.tvRuleSeq);
        tvPwMatch  = findViewById(R.id.tvPwMatch);

        // 초기 규칙 상태
        setRule(tvRuleLen, false, "길이 10~16자");
        setRule(tvRuleMix, false, "영문 대/소문자/숫자 중 2종류 이상, 영문/숫자만 사용");
        setRule(tvRuleSeq, false, "연속 문자/숫자·키보드 시퀀스 3자리 이상 금지");
        tvPwMatch.setText("비밀번호 일치 여부");
        tvPwMatch.setTextColor(COLOR_WEAK);
    }

    private void setupEmailSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, domains
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
                // 도메인(스피너) 변경 시에는 인증 해제
                emailVerified.set(false);
                updateSubmitEnabled();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        btnBackToSpinner.setOnClickListener(v -> {
            customDomainWrapper.setVisibility(View.GONE);
            spinnerEmailDomain.setVisibility(View.VISIBLE);
            spinnerEmailDomain.setSelection(0);
            editTextDomainCustom.setText(null);
            // 직접입력 → 스피너로 되돌릴 때도 인증 해제
            emailVerified.set(false);
            updateSubmitEnabled();
        });
    }

    private void setupClicks() {
        // 회원가입
        btnSubmitRegister.setOnClickListener(v -> {
            if (!validateInputs()) return;
            if (!emailVerified.get()) {
                toast("이메일 인증을 완료해 주세요.");
                return;
            }
            doRegister();
        });

        // 뒤로가기 → 로그인
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // 아이디 / 이메일 중복 확인
        btnCheckId.setOnClickListener(v -> doCheckDuplicate());

        // 인증 메일 발송/검증
        btnSendCode.setOnClickListener(v -> sendEmailCode());
        btnVerifyCode.setOnClickListener(v -> verifyEmailCode());
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

    /** 폼 유효성 — 드라이버앱 기준 회사명 필수 */
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

        if (customDomainWrapper.getVisibility() == View.VISIBLE) {
            String customDomain = safeText(editTextDomainCustom).trim();
            if (customDomain.isEmpty()) { toast("도메인을 입력하세요"); return false; }
            if (!customDomain.contains(".") || customDomain.startsWith(".") || customDomain.endsWith(".")) {
                toast("도메인 형식을 확인하세요"); return false;
            }
        }
        String email = composeEmail();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { toast("이메일 형식을 확인하세요"); return false; }

        if (phone.isEmpty()) { toast("전화번호를 입력하세요"); return false; }
        return true;
    }

    // ── 중복 확인 (/auth/check) ───────────────────────────────────────────────
    private void doCheckDuplicate() {
        String userid = safeText(editTextId).trim();
        if (userid.isEmpty()) {
            toast("아이디를 입력하세요");
            return;
        }

        // 이메일은 '있으면' 함께 검사, 아니면 userid만 검사
        String emailCandidate = composeEmail();
        String emailForCheck = Patterns.EMAIL_ADDRESS.matcher(emailCandidate).matches()
                ? emailCandidate
                : null; // null이면 Retrofit이 쿼리 파라미터를 생략함

        btnCheckId.setEnabled(false);
        api.checkDup(userid, emailForCheck).enqueue(new Callback<ApiService.CheckResponse>() {
            @Override public void onResponse(Call<ApiService.CheckResponse> call, Response<ApiService.CheckResponse> res) {
                btnCheckId.setEnabled(true);
                if (!res.isSuccessful() || res.body() == null) {
                    toast("중복확인 실패 (" + res.code() + ")");
                    return;
                }
                ApiService.CheckResponse body = res.body();

                if (body.useridTaken) {
                    toast("이미 사용 중인 아이디입니다.");
                    return;
                }
                if (emailForCheck != null && body.emailTaken) {
                    toast("아이디는 사용 가능하지만, 이메일은 이미 사용 중입니다.");
                    return;
                }

                if (emailForCheck == null) {
                    toast("사용 가능한 아이디입니다. (이메일은 입력 후 형식 확인 가능)");
                } else {
                    toast("사용 가능한 아이디/이메일입니다.");
                }
            }
            @Override public void onFailure(Call<ApiService.CheckResponse> call, Throwable t) {
                btnCheckId.setEnabled(true);
                toast("네트워크 오류: " + t.getMessage());
            }
        });
    }

    // ── 이메일 인증 (REGISTER) ────────────────────────────────────────────────
    private void sendEmailCode() {
        String email = composeEmail();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("이메일 형식을 확인하세요");
            return;
        }
        btnSendCode.setEnabled(false);
        btnVerifyCode.setEnabled(false);
        codeInput.setEnabled(false);

        ApiService.SendEmailCodeRequest req = new ApiService.SendEmailCodeRequest(email, "REGISTER");
        api.sendEmail(req).enqueue(new Callback<Map<String, Object>>() {
            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    toast("인증 메일 발송 실패 (" + res.code() + ")");
                    resetEmailUi();
                    return;
                }
                Object v = res.body().get("verificationId");
                verificationId = (v == null) ? null : String.valueOf(v);
                if (verificationId == null) {
                    toast("인증 토큰 발급 실패");
                    resetEmailUi();
                    return;
                }
                emailVerified.set(false);
                startEmailTimer(10 * 60);
                codeInput.setText("");
                codeInput.setEnabled(true);
                btnVerifyCode.setEnabled(true);
                toast("인증 메일을 보냈습니다.");
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                toast("네트워크 오류: " + t.getMessage());
                resetEmailUi();
            }
        });
    }

    private void verifyEmailCode() {
        if (verificationId == null) { toast("먼저 인증 메일을 발송하세요"); return; }
        String email = composeEmail();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { toast("이메일 형식을 확인하세요"); return; }
        String code = safeText(codeInput).trim();
        if (TextUtils.isEmpty(code)) { toast("인증코드를 입력하세요"); return; }

        btnVerifyCode.setEnabled(false);

        ApiService.VerifyEmailCodeRequest req =
                new ApiService.VerifyEmailCodeRequest(verificationId, email, "REGISTER", code);
        api.verifyEmail(req).enqueue(new Callback<Map<String, Object>>() {
            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                btnVerifyCode.setEnabled(true);
                if (!res.isSuccessful() || res.body() == null) {
                    toast("인증 실패 (" + res.code() + ")");
                    return;
                }
                Object ok = res.body().get("ok");
                if (ok instanceof Boolean && (Boolean) ok) {
                    emailVerified.set(true);
                    stopEmailTimer();
                    textEmailTimer.setText("인증 완료");
                    toast("이메일 인증 완료");
                    updateSubmitEnabled();
                } else {
                    toast("인증 실패");
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                btnVerifyCode.setEnabled(true);
                toast("네트워크 오류: " + t.getMessage());
            }
        });
    }

    private void startEmailTimer(int seconds) {
        stopEmailTimer();
        emailTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override public void onTick(long ms) {
                long s = ms / 1000;
                textEmailTimer.setText(String.format(Locale.KOREA, "남은 시간: %02d:%02d", s/60, s%60));
            }
            @Override public void onFinish() {
                textEmailTimer.setText("만료됨");
                verificationId = null;
                emailVerified.set(false);
                btnVerifyCode.setEnabled(false);
                updateSubmitEnabled();
            }
        };
        emailTimer.start();
    }

    private void stopEmailTimer() {
        if (emailTimer != null) { emailTimer.cancel(); emailTimer = null; }
    }

    private void resetEmailUi() {
        stopEmailTimer();
        textEmailTimer.setText("남은 시간: -");
        verificationId = null;
        emailVerified.set(false);
        btnSendCode.setEnabled(true);
        btnVerifyCode.setEnabled(false);
        codeInput.setEnabled(false);
        updateSubmitEnabled();
    }

    // ── 회원가입 (/auth/register) ─────────────────────────────────────────────
    private void doRegister() {
        // 마지막 유효성 한 번 더
        if (!validateInputs()) return;
        if (!emailVerified.get()) {
            toast("이메일 인증을 완료해 주세요.");
            return;
        }

        String userid  = safeText(editTextId).trim();
        String pw      = safeText(editTextPw);
        String name    = safeText(editTextName).trim();
        String company = safeText(editTextCompany).trim();
        String email   = composeEmail();
        String phone   = safeText(editTextPhone).trim();

        // RegisterCheckActivity에서 넘겨준 면허 정보/사진/생년월일/면허상 이름
        Intent fromCheck = getIntent();
        String licenseNumber      = fromCheck.getStringExtra(RegisterCheckActivity.EXTRA_LICENSE_NUMBER);
        String acquiredDate       = fromCheck.getStringExtra(RegisterCheckActivity.EXTRA_ACQUIRED_DATE);
        String licensePhotoUriStr = fromCheck.getStringExtra(RegisterCheckActivity.EXTRA_LICENSE_PHOTO_URI);
        String licenseHolderName  = fromCheck.getStringExtra(RegisterCheckActivity.EXTRA_NAME);   // 면허증상 이름
        String birthDate          = fromCheck.getStringExtra(RegisterCheckActivity.EXTRA_BIRTH);  // yyyy-MM-dd

        // 필수 면허정보 체크(서버에서 에러 나기 전에 클라에서 1차 방어)
        if (TextUtils.isEmpty(licenseNumber) || TextUtils.isEmpty(acquiredDate)) {
            toast("면허 정보가 누락되었습니다. 다시 촬영/입력해 주세요.");
            return;
        }
        if (TextUtils.isEmpty(licensePhotoUriStr)) {
            toast("면허 사진이 누락되었습니다. 다시 촬영해 주세요.");
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("userid", userid);
            data.put("password", pw);
            data.put("username", name);
            data.put("email", email);
            data.put("tel", phone);
            data.put("company", company);
            data.put("clientType", clientType);
            data.put("deviceId", deviceId);

            // 이메일 인증 키 이름: verificationId (서버 규격)
            if (!TextUtils.isEmpty(verificationId)) {
                data.put("verificationId", verificationId);
            }

            // 드라이버 필수/확장 필드
            data.put("licenseNumber", licenseNumber);
            data.put("acquiredDate", acquiredDate); // yyyy-MM-dd
            if (!TextUtils.isEmpty(birthDate))        data.put("birthDate",   birthDate);        // yyyy-MM-dd
            if (!TextUtils.isEmpty(licenseHolderName)) data.put("licenseName", licenseHolderName);

            RequestBody dataJson = RequestBody.create(
                    data.toString().getBytes(StandardCharsets.UTF_8),
                    MediaType.parse("application/json; charset=utf-8")
            );

            // 프로필은 선택사항 → 이번 플로우에선 null
            MultipartBody.Part profile = null;

            // 면허 사진(필수) 멀티파트 생성
            MultipartBody.Part license = uriToImagePart(
                    "licensePhoto",
                    licensePhotoUriStr,
                    "license.jpg"
            );

            setAllEnabled(false);
            api.register(dataJson, profile, license).enqueue(new Callback<ApiService.RegisterResponse>() {
                @Override public void onResponse(Call<ApiService.RegisterResponse> call,
                                                 Response<ApiService.RegisterResponse> res) {
                    setAllEnabled(true);
                    if (!res.isSuccessful() || res.body() == null) {
                        if (res.code() == 409) {
                            toast("이미 사용 중인 아이디/이메일/면허번호가 있습니다.");
                        } else if (res.code() == 400) {
                            toast("가입 정보가 올바르지 않습니다.");
                        } else if (res.code() == 413) {
                            toast("이미지가 너무 큽니다.");
                        } else {
                            toast("회원가입 실패 (" + res.code() + ")");
                        }
                        return;
                    }
                    ApiService.RegisterResponse body = res.body();
                    if (body.ok) {
                        toast("회원가입이 완료되었습니다.");
                        startActivity(new Intent(RegisterActivity.this, RegisterComActivity.class));
                        finish();
                    } else {
                        String reason = TextUtils.isEmpty(body.reason) ? "실패" : body.reason;
                        toast("회원가입 실패: " + reason);
                    }
                }
                @Override public void onFailure(Call<ApiService.RegisterResponse> call, Throwable t) {
                    setAllEnabled(true);
                    toast("네트워크 오류: " + t.getMessage());
                }
            });

        } catch (Exception e) {
            toast("요청 생성 오류: " + e.getMessage());
        }
    }

    /** content:// 또는 file:// URI → Multipart 이미지 파트 (하위 호환) */
    private MultipartBody.Part uriToImagePart(String formName, String uriStr, String fallbackName) {
        if (TextUtils.isEmpty(uriStr)) return null;
        try {
            android.net.Uri uri = android.net.Uri.parse(uriStr);
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "image/*";

            byte[] bytes;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                bytes = readAllBytesCompat(is);
            }

            RequestBody body = RequestBody.create(bytes, MediaType.parse(mime));
            return MultipartBody.Part.createFormData(formName, fallbackName, body);
        } catch (Exception e) {
            toast("이미지 변환 실패: " + e.getMessage());
            return null;
        }
    }

    /** InputStream → byte[] (API 21+ 호환) */
    private static byte[] readAllBytesCompat(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int r;
        while ((r = is.read(buf)) != -1) {
            bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }

    private void setAllEnabled(boolean enabled) {
        btnSubmitRegister.setEnabled(enabled && emailVerified.get());
        btnCheckId.setEnabled(enabled && !safeText(editTextId).trim().isEmpty());
        btnSendCode.setEnabled(enabled);
        btnVerifyCode.setEnabled(enabled && verificationId != null);
        editTextId.setEnabled(enabled);
        editTextPw.setEnabled(enabled);
        editTextPwConfirm.setEnabled(enabled);
        editTextName.setEnabled(enabled);
        editTextCompany.setEnabled(enabled);
        editTextEmail.setEnabled(enabled);
        editTextDomainCustom.setEnabled(enabled);
        spinnerEmailDomain.setEnabled(enabled);
        editTextPhone.setEnabled(enabled);
        codeInput.setEnabled(enabled && verificationId != null);
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

        boolean lenOk = pw.length() >= 10 && pw.length() <= 16 && pw.matches("^[A-Za-z0-9]+$");
        setRule(tvRuleLen, lenOk, "길이 10~16자");

        boolean hasUpper=false, hasLower=false, hasDigit=false;
        for (int i=0; i<pw.length(); i++) {
            char c = pw.charAt(i);
            if (c>='A'&&c<='Z') hasUpper=true;
            else if (c>='a'&&c<='z') hasLower=true;
            else if (c>='0'&&c<='9') hasDigit=true;
        }
        int classes = (hasUpper?1:0)+(hasLower?1:0)+(hasDigit?1:0);
        setRule(tvRuleMix, classes >= 2, "영문 대/소문자/숫자 중 2종류 이상, 영문/숫자만 사용");

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
        if (d.matches("^(15|16|18)\\d{6,8}$")) {
            if (d.length() >= 8) {
                return d.substring(0,4) + "-" + d.substring(4, Math.min(8, d.length()));
            }
            return d;
        }
        if (d.startsWith("02")) {
            if (d.length() <= 2) return d;
            if (d.length() <= 5) return d.substring(0,2) + "-" + d.substring(2);
            if (d.length() <= 9)  return d.substring(0,2) + "-" + d.substring(2,5) + "-" + d.substring(5);
            return d.substring(0,2) + "-" + d.substring(2,6) + "-" + d.substring(6, Math.min(10, d.length()));
        }
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
        if (!s.matches("^[A-Za-z0-9]+$")) return false;

        boolean hasUpper=false, hasLower=false, hasDigit=false;
        for (int i=0; i<len; i++) {
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
                "qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890", "0987654321"
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
        // 일반 필드 → 인증 상태 유지
        SimpleTextWatcher normalWatcher = new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                updateSubmitEnabled();
            }
        };
        editTextId.addTextChangedListener(normalWatcher);
        editTextName.addTextChangedListener(normalWatcher);
        editTextCompany.addTextChangedListener(normalWatcher);
        editTextPhone.addTextChangedListener(normalWatcher);

        // 이메일 관련 필드 → 인증 상태 해제
        SimpleTextWatcher emailWatcher = new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                emailVerified.set(false);
                updateSubmitEnabled();
            }
        };
        editTextEmail.addTextChangedListener(emailWatcher);
        editTextDomainCustom.addTextChangedListener(emailWatcher);
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

        boolean emailOk;
        if (customDomainWrapper.getVisibility() == View.VISIBLE) {
            String customDomain = safeText(editTextDomainCustom).trim();
            emailOk = !TextUtils.isEmpty(safeText(editTextEmail).trim())
                    && !customDomain.isEmpty()
                    && customDomain.contains(".")
                    && !customDomain.startsWith(".")
                    && !customDomain.endsWith(".");
        } else {
            emailOk = !TextUtils.isEmpty(safeText(editTextEmail).trim());
        }

        // 회원가입 버튼은 이메일 인증까지 끝나야 활성화
        btnSubmitRegister.setEnabled(baseOk && pwOk && emailOk && emailVerified.get());

        // 아이디 중복확인은 아이디만 있으면 가능
        btnCheckId.setEnabled(!id.isEmpty());
    }

    // ── 유틸 ───────────────────────────────────────────────────────────────────
    private String safeText(EditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onDestroy() {
        stopEmailTimer();
        super.onDestroy();
    }

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
