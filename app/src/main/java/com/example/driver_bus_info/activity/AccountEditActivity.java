package com.example.driver_bus_info.activity;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 계정 정보 수정
 * - 이름/이메일/전화번호 수정 (/users/me PATCH multipart)
 * - 이메일 인증 발송/검증 (이메일 "변경"시에만 허용)
 * - 비밀번호 변경 (/users/me/password) — 현재 비밀번호 포함, 성공 시 세션 revoke → 로그아웃
 * - 스피너 도메인 ↔ 직접입력 전환
 * - 새 비밀번호 규칙 3가지 실시간 표시(입력 없음도 실패/빨강)
 */
public class AccountEditActivity extends AppCompatActivity {

    // 헤더
    private ImageButton btnBack;

    // 입력들
    private TextView editId;

    private EditText editPasswordCurrent;   // 현재 비밀번호
    private EditText editPassword;          // 새 비밀번호
    private EditText editPasswordConfirm;   // 새 비밀번호 확인

    // 규칙 3줄
    private TextView tvRuleLen, tvRuleMix, tvRuleSeq;

    private EditText editName;

    private EditText editEmailId;
    private Spinner spinnerEmailDomain;
    private LinearLayout customDomainWrapper;
    private EditText editDomainCustom;
    private ImageButton btnBackToSpinner;

    private TextView textTimer;

    private EditText editPhone;

    // 버튼
    private Button btnSendCode;
    private Button btnVerifyCode;
    private EditText codeInput;
    private Button btnUpdate;

    // 팝업
    private Dialog dialog2;

    // 상태
    private TokenManager tm;
    private ApiService api;
    private String clientType;
    private String verificationId;                 // 서버가 발급한 인증 요청 id
    private final AtomicBoolean emailVerified = new AtomicBoolean(false);
    private CountDownTimer timer;

    private String originalEmail = null;           // 서버에서 받아온 기존 이메일
    private String originalName = null;
    private String originalTel = null;

    // 이메일 도메인 데이터
    private static final String[] DOMAINS = new String[]{
            "gmail.com", "naver.com", "daum.net", "kakao.com", "nate.com", "직접입력"
    };

    // 색상 (성공/실패 표시)
    private static final int COLOR_OK   = 0xFF2E7D32; // 녹색
    private static final int COLOR_FAIL = 0xFFB00020; // 붉은색

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit);

        bindViews();
        setupEmailDomainUi();
        wireButtons();
        wireEmailChangeWatchers(); // 이메일 변경 감지
        wirePwRuleWatcher();       // 비번 규칙 실시간 반영

        tm = TokenManager.get(this);
        api = ApiClient.get(this);
        clientType = DeviceInfo.getClientType();

        // 서버의 내 정보로 기본값 채우기
        preloadMe();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.account_back_button);

        editId = findViewById(R.id.edit_id);

        editPasswordCurrent = findViewById(R.id.edit_password_current);
        editPassword        = findViewById(R.id.edit_password);
        editPasswordConfirm = findViewById(R.id.edit_password_confirm);

        tvRuleLen = findViewById(R.id.tv_rule_len);
        tvRuleMix = findViewById(R.id.tv_rule_mix);
        tvRuleSeq = findViewById(R.id.tv_rule_seq);

        editName = findViewById(R.id.edit_name);

        editEmailId = findViewById(R.id.edit_email_id);
        spinnerEmailDomain = findViewById(R.id.spinner_email_domain);
        customDomainWrapper = findViewById(R.id.custom_domain_wrapper);
        editDomainCustom = findViewById(R.id.editTextDomainCustom);
        btnBackToSpinner = findViewById(R.id.btn_back_to_spinner);

        textTimer = findViewById(R.id.text_timer);

        editPhone = findViewById(R.id.edit_phone);

        btnSendCode = findViewById(R.id.btn_send_code);
        btnVerifyCode = findViewById(R.id.btn_verify_code);
        codeInput = findViewById(R.id.code_input);

        btnUpdate = findViewById(R.id.btn_update);

        // 초기 상태: 인증 관련 버튼 비활성화(변경 시에만 활성)
        setVerificationUiEnabled(false);
        textTimer.setText("남은 시간: -");

        // 규칙 초기색 — 전부 실패(빨강)
        setRule(tvRuleLen, false);
        setRule(tvRuleMix, false);
        setRule(tvRuleSeq, false);
    }

    private void setupEmailDomainUi() {
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DOMAINS);
        spinnerEmailDomain.setAdapter(ad);
        spinnerEmailDomain.setSelection(0);

        spinnerEmailDomain.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                String sel = DOMAINS[pos];
                if ("직접입력".equals(sel)) {
                    spinnerEmailDomain.setVisibility(View.GONE);
                    customDomainWrapper.setVisibility(View.VISIBLE);
                    editDomainCustom.requestFocus();
                } else {
                    customDomainWrapper.setVisibility(View.GONE);
                    spinnerEmailDomain.setVisibility(View.VISIBLE);
                }
                // 선택 바뀌면 변경여부 재평가
                onEmailInputChanged();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnBackToSpinner.setOnClickListener(v -> {
            customDomainWrapper.setVisibility(View.GONE);
            spinnerEmailDomain.setVisibility(View.VISIBLE);
            spinnerEmailDomain.setSelection(0);
        });
    }

    private void wireButtons() {
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        btnSendCode.setOnClickListener(v -> sendEmailCode());
        btnVerifyCode.setOnClickListener(v -> verifyEmailCode());
        btnUpdate.setOnClickListener(v -> doUpdateFlow());
    }

    /** 이메일 입력(아이디/도메인/커스텀도메인)이 바뀌면 변경여부 판단해서 인증 UI 제어 */
    private void wireEmailChangeWatchers() {
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { onEmailInputChanged(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        editEmailId.addTextChangedListener(w);
        editDomainCustom.addTextChangedListener(w);
    }

    /** 새 비밀번호 입력 시 규칙 3줄 실시간 반영(빈값도 모두 실패/빨강) */
    private void wirePwRuleWatcher() {
        editPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePwRules(safe(s));
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        updatePwRules(safe(editPassword.getText()));
    }

    private void onEmailInputChanged() {
        boolean changed = isEmailChanged();
        if (!changed) {
            // 원래 메일과 동일 → 인증 관련 초기화 & 비활성화
            emailVerified.set(false);
            verificationId = null;
            stopTimer();
            textTimer.setText("남은 시간: -");
            setVerificationUiEnabled(false);
        } else {
            // 변경됨 → 인증 가능
            emailVerified.set(false);
            verificationId = null;
            stopTimer();
            textTimer.setText("남은 시간: -");
            setVerificationUiEnabled(true);
        }
    }

    private void setVerificationUiEnabled(boolean enabled) {
        btnSendCode.setEnabled(enabled);
        btnVerifyCode.setEnabled(enabled);
        codeInput.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.5f;
        btnSendCode.setAlpha(alpha);
        btnVerifyCode.setAlpha(alpha);
        codeInput.setAlpha(alpha);
    }

    /** 서버 /users/me 불러와서 필드 채우기 */
    private void preloadMe() {
        String bearer = (tm.tokenType() == null ? "Bearer" : tm.tokenType()) + " " + tm.accessToken();
        api.me(bearer, clientType).enqueue(new Callback<ApiService.UserResponse>() {
            @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                ApiService.UserResponse u = res.body();

                editId.setText(safe(u.userid));

                originalName = safe(u.username);
                originalTel  = safe(u.tel);
                if (!TextUtils.isEmpty(u.username)) editName.setText(u.username);
                if (!TextUtils.isEmpty(u.tel)) editPhone.setText(u.tel);

                originalEmail = safe(u.email); // 원본 이메일 저장

                // 이메일을 분리해서 넣기(아이디/도메인)
                if (!TextUtils.isEmpty(originalEmail) && originalEmail.contains("@")) {
                    String[] parts = originalEmail.split("@", 2);
                    editEmailId.setText(parts[0]);
                    String dom = parts[1].toLowerCase(Locale.ROOT);
                    int idx = indexOfDomain(dom);
                    if (idx >= 0) {
                        spinnerEmailDomain.setSelection(idx);
                        customDomainWrapper.setVisibility(View.GONE);
                        spinnerEmailDomain.setVisibility(View.VISIBLE);
                    } else {
                        // 직접입력 모드로 전환
                        spinnerEmailDomain.setSelection(DOMAINS.length - 1);
                        spinnerEmailDomain.setVisibility(View.GONE);
                        customDomainWrapper.setVisibility(View.VISIBLE);
                        editDomainCustom.setText(dom);
                    }
                } else {
                    // 이메일 없음 → 인증은 변경 시에만 가능
                    setVerificationUiEnabled(false);
                }

                // 로드 후 한번 상태 동기화
                onEmailInputChanged();
            }
            @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {}
        });
    }

    private int indexOfDomain(String d) {
        for (int i=0;i<DOMAINS.length;i++) if (DOMAINS[i].equalsIgnoreCase(d)) return i;
        return -1;
    }

    // ── 이메일 인증 (변경시에만 허용) ────────────────────────────────────────────

    private void sendEmailCode() {
        if (!isEmailChanged()) { toast("이메일을 변경해야 인증이 가능합니다."); return; }

        String email = buildEmail();
        if (email == null) { toast("이메일을 올바르게 입력하세요"); return; }

        ApiService.SendEmailCodeRequest req = new ApiService.SendEmailCodeRequest(email, "CHANGE_EMAIL");
        api.sendEmail(req).enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> res) {
                if (!res.isSuccessful() || res.body() == null) { toast("인증 메일 발송 실패"); return; }
                Object v = res.body().get("verificationId");
                verificationId = (v == null) ? null : String.valueOf(v);
                if (verificationId == null) { toast("인증 토큰 발급 실패"); return; }
                emailVerified.set(false);
                startTimer(5 * 60); // 5분
                toast("인증 메일을 보냈습니다");
            }
            @Override public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) { toast("네트워크 오류: " + t.getMessage()); }
        });
    }

    private void verifyEmailCode() {
        if (!isEmailChanged()) { toast("이메일을 변경해야 인증이 가능합니다."); return; }
        if (verificationId == null) { toast("먼저 인증 메일을 발송하세요"); return; }
        String email = buildEmail();
        if (email == null) { toast("이메일을 올바르게 입력하세요"); return; }
        String code = safe(codeInput.getText());
        if (TextUtils.isEmpty(code)) { toast("인증코드를 입력하세요"); return; }

        ApiService.VerifyEmailCodeRequest req = new ApiService.VerifyEmailCodeRequest(verificationId, email, "CHANGE_EMAIL", code);
        api.verifyEmail(req).enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> res) {
                if (!res.isSuccessful() || res.body() == null) { toast("인증 실패"); return; }
                Boolean ok = (Boolean) res.body().get("ok");
                if (Boolean.TRUE.equals(ok)) {
                    emailVerified.set(true);
                    stopTimer();
                    textTimer.setText("인증 완료");
                    toast("이메일 인증 완료");
                } else {
                    toast("인증 실패");
                }
            }
            @Override public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) { toast("네트워크 오류: " + t.getMessage()); }
        });
    }

    private void startTimer(int seconds) {
        stopTimer();
        timer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override public void onTick(long ms) {
                long s = ms / 1000;
                textTimer.setText(String.format(Locale.KOREA, "남은 시간: %02d:%02d", s/60, s%60));
            }
            @Override public void onFinish() {
                textTimer.setText("만료됨");
                verificationId = null;
            }
        };
        timer.start();
    }

    private void stopTimer() {
        if (timer != null) { timer.cancel(); timer = null; }
    }

    // ── 업데이트 플로우 ──────────────────────────────────────────────────────────

    private void doUpdateFlow() {
        String curPw = safe(editPasswordCurrent.getText());
        String newPw = safe(editPassword.getText());
        String newPw2 = safe(editPasswordConfirm.getText());

        boolean wantChangePw = !TextUtils.isEmpty(newPw) || !TextUtils.isEmpty(newPw2);

        if (wantChangePw) {
            if (TextUtils.isEmpty(curPw)) { toast("현재 비밀번호를 입력하세요"); return; }
            if (!newPw.equals(newPw2)) { toast("비밀번호가 일치하지 않습니다"); return; }
            if (!isPwValid(newPw)) { toast("비밀번호 규칙을 확인하세요 (10~16자, 영문/숫자, 연속/키보드열 금지)"); return; }
        }

        // (1) 프로필 먼저 업데이트
        updateProfile(() -> {
            // (2) 프로필 성공 후, 비밀번호 변경이 요청되었다면 진행
            if (wantChangePw) {
                changePassword(curPw, newPw);
            } else {
                // 비번 변경 없으면 완료 팝업
                showUserEditComPopup();
            }
        });
    }

    private boolean isPwValid(String pw) {
        if (pw == null) return false;
        int len = pw.length();
        if (len < 10 || len > 16) return false;

        // 영문/숫자만
        if (!pw.matches("^[A-Za-z0-9]+$")) return false;

        boolean hasUpper=false, hasLower=false, hasDigit=false;
        for (int i=0;i<len;i++) {
            char c = pw.charAt(i);
            if (c>='A'&&c<='Z') hasUpper=true;
            else if (c>='a'&&c<='z') hasLower=true;
            else if (c>='0'&&c<='9') hasDigit=true;
        }
        int classes = (hasUpper?1:0) + (hasLower?1:0) + (hasDigit?1:0);
        if (classes < 2) return false;

        if (hasSequentialAlphaOrDigit(pw)) return false;
        if (hasKeyboardSequence(pw)) return false;

        return true;
    }

    private void updateProfile(Runnable onSuccess) {
        boolean emailChanged = isEmailChanged();
        String email = emailChanged ? buildEmail() : null;

        String name  = safe(editName.getText());
        String tel   = safe(editPhone.getText());

        // 아무 것도 안 바뀌면 스킵하고 다음 단계로
        boolean profileChanged =
                (emailChanged && !TextUtils.isEmpty(email)) ||
                        (!TextUtils.isEmpty(name) && !name.equals(originalName)) ||
                        (!TextUtils.isEmpty(tel)  && !tel.equals(originalTel));

        if (emailChanged && !emailVerified.get()) {
            toast("이메일 변경은 인증 완료 후 가능합니다");
            return;
        }

        if (!profileChanged) {
            onSuccess.run();
            return;
        }

        try {
            org.json.JSONObject data = new org.json.JSONObject();
            if (!TextUtils.isEmpty(name)) data.put("username", name);
            if (!TextUtils.isEmpty(tel))  data.put("tel", tel);
            if (emailChanged && !TextUtils.isEmpty(email)) {
                data.put("email", email);
                data.put("emailVerificationId", verificationId);
            }

            RequestBody dataJson = RequestBody.create(
                    data.toString().getBytes(StandardCharsets.UTF_8),
                    MediaType.parse("application/json; charset=utf-8")
            );

            MultipartBody.Part filePart = null; // 이번 화면엔 프로필 이미지 업로드 없음

            String bearer = (tm.tokenType() == null ? "Bearer" : tm.tokenType()) + " " + tm.accessToken();
            api.updateMe(bearer, clientType, dataJson, filePart)
                    .enqueue(new Callback<ApiService.UserResponse>() {
                        @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                            if (res.isSuccessful()) {
                                // 성공 후 원본 값 갱신 & 이메일 인증 상태 초기화
                                originalName = name.isEmpty() ? originalName : name;
                                originalTel  = tel.isEmpty()  ? originalTel  : tel;
                                if (emailChanged && email != null) {
                                    originalEmail = email;
                                    emailVerified.set(false);
                                    verificationId = null;
                                    stopTimer();
                                    textTimer.setText("남은 시간: -");
                                    setVerificationUiEnabled(false);
                                }
                                onSuccess.run();
                            } else if (res.code() == 409) {
                                toast("이미 사용 중인 이메일입니다");
                            } else if (res.code() == 400) {
                                toast("요청 형식이 올바르지 않습니다");
                            } else {
                                toast("수정 실패 (" + res.code() + ")");
                            }
                        }
                        @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                            toast("네트워크 오류: " + t.getMessage());
                        }
                    });

        } catch (Exception e) {
            toast("요청 생성 오류: " + e.getMessage());
        }
    }

    /** 비밀번호 변경: 성공 시 세션 revoke → 토큰 비움 & 로그인 화면으로 */
    private void changePassword(String currentPw, String newPw) {
        String bearer = (tm.tokenType() == null ? "Bearer" : tm.tokenType()) + " " + tm.accessToken();
        ApiService.ChangePasswordRequest body = new ApiService.ChangePasswordRequest(currentPw, newPw);

        api.changePassword(bearer, clientType, body).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> res) {
                if (res.isSuccessful()) {
                    toast("비밀번호가 변경되어 다시 로그인합니다.");
                    // 서버에서 세션 revoke되므로 로컬 토큰도 정리하고 로그인으로
                    try { tm.clear(); } catch (Exception ignore) {}
                    Intent i = new Intent(AccountEditActivity.this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                } else if (res.code() == 400) {
                    toast("현재 비밀번호가 올바르지 않거나 정책 위반입니다.");
                } else {
                    toast("비밀번호 변경 실패 (" + res.code() + ")");
                }
            }
            @Override public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                toast("네트워크 오류: " + t.getMessage());
            }
        });
    }

    // ── 팝업 ────────────────────────────────────────────────────────────────────

    private void showUserEditComPopup() {
        dialog2 = new Dialog(this);
        dialog2.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog2.setContentView(R.layout.popup_user_edit_com);
        if (dialog2.getWindow() != null) {
            dialog2.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            dialog2.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        Button btnOk = dialog2.findViewById(R.id.bthOk);
        btnOk.setOnClickListener(v -> {
            dialog2.dismiss();
            finish(); // 단순 복귀
        });
        dialog2.show();
    }

    // ── 유틸 ───────────────────────────────────────────────────────────────────

    @Nullable
    private String buildEmail() {
        String id = safe(editEmailId.getText());
        if (TextUtils.isEmpty(id)) return null;
        String domain;
        if (customDomainWrapper.getVisibility() == View.VISIBLE) {
            domain = safe(editDomainCustom.getText());
        } else {
            domain = (String) spinnerEmailDomain.getSelectedItem();
            if ("직접입력".equals(domain)) domain = safe(editDomainCustom.getText());
        }
        if (TextUtils.isEmpty(domain)) return null;
        String email = id + "@" + domain;
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) return null;
        return email;
    }

    /** 현재 입력된 이메일이 기존 이메일과 다른지 판단 */
    private boolean isEmailChanged() {
        String current = buildEmail();
        String original = safe(originalEmail);
        if (TextUtils.isEmpty(current) && TextUtils.isEmpty(original)) return false;
        return !safe(current).equalsIgnoreCase(original);
    }

    private String safe(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        super.onDestroy();
    }

    // ── 비밀번호 규칙(서버 PasswordPolicy와 맞춤) ──

    /** 입력이 비었으면 전부 실패(빨강)로 표시 */
    private void updatePwRules(String pw) {
        if (TextUtils.isEmpty(pw)) {
            setRule(tvRuleLen, false);
            setRule(tvRuleMix, false);
            setRule(tvRuleSeq, false);
            return;
        }

        // 1) 길이/문자군: 10~16, 영문/숫자만
        boolean lenOk = pw.length() >= 10 && pw.length() <= 16 && pw.matches("^[A-Za-z0-9]+$");
        setRule(tvRuleLen, lenOk);

        // 2) 2가지 이상 조합(대/소/숫자)
        boolean hasUpper=false, hasLower=false, hasDigit=false;
        for (int i=0;i<pw.length();i++) {
            char c = pw.charAt(i);
            if (c>='A'&&c<='Z') hasUpper=true;
            else if (c>='a'&&c<='z') hasLower=true;
            else if (c>='0'&&c<='9') hasDigit=true;
        }
        int classes = (hasUpper?1:0)+(hasLower?1:0)+(hasDigit?1:0);
        setRule(tvRuleMix, classes >= 2);

        // 3) 연속/키보드 시퀀스 금지
        boolean badSeq = hasSequentialAlphaOrDigit(pw) || hasKeyboardSequence(pw);
        setRule(tvRuleSeq, !badSeq);
    }

    private void setRule(TextView tv, boolean ok) {
        tv.setTextColor(ok ? COLOR_OK : COLOR_FAIL);
    }

    private boolean hasSequentialAlphaOrDigit(String s) {
        if (s == null || s.length() < 3) return false;
        for (int i = 0; i <= s.length() - 3; i++) {
            char a = s.charAt(i), b = s.charAt(i+1), c = s.charAt(i+2);
            // 모두 숫자
            if (Character.isDigit(a) && Character.isDigit(b) && Character.isDigit(c)) {
                int x=a, y=b, z=c;
                if ((y==x+1 && z==y+1) || (y==x-1 && z==y-1)) return true;
            }
            // 모두 알파벳(대소문자 무시)
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
            // 증가 방향
            for (int i=0; i<=row.length()-3; i++) {
                String sub = row.substring(i, i+3);
                if (lower.contains(sub)) return true;
            }
            // 감소 방향
            String rev = new StringBuilder(row).reverse().toString();
            for (int i=0; i<=rev.length()-3; i++) {
                String sub = rev.substring(i, i+3);
                if (lower.contains(sub)) return true;
            }
        }
        return false;
    }
}
