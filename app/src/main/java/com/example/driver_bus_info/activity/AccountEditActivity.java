package com.example.driver_bus_info.activity;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.OpenableColumns;
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

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 계정 정보 수정
 * - 이름(보기 전용)/이메일/전화번호/(드라이버)회사명 수정
 * - 이메일 인증 발송/검증 (이메일 "변경"시에만 허용)
 * - 비밀번호 변경 — 성공 시 조용히 재로그인 후 메인으로 이동
 * - 스피너 도메인 ↔ 직접입력 전환
 * - 새 비밀번호 규칙 3가지 + 비밀번호 일치 여부 실시간 표시
 * - 전화번호 입력 시 자동 하이픈 포맷팅
 * - 프로필 이미지: 최초 진입 시 서버 이미지 미리보기, 선택 시 변경 업로드, 미선택시 유지
 */
public class AccountEditActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView editId;

    // MainActivity로부터 넘겨받은 검증된 현재 비밀번호
    private String verifiedPasswordFromGate;

    private EditText editPassword;
    private EditText editPasswordConfirm;

    private TextView tvRuleLen, tvRuleMix, tvRuleSeq, tvPwMatch;

    private EditText editName;          // 보기 전용

    // 회사명(드라이버 전용)
    private EditText editCompany;
    private View companyLayout;

    private EditText editEmailId;
    private Spinner spinnerEmailDomain;
    private LinearLayout customDomainWrapper;
    private EditText editDomainCustom;
    private ImageButton btnBackToSpinner;

    private TextView textTimer;
    private EditText editPhone;

    private Button btnSendCode;
    private Button btnVerifyCode;
    private EditText codeInput;
    private Button btnUpdate;

    // ===== 프로필 이미지 UI =====
    private CircleImageView ivProfilePreview;
    private View btnPickImage;
    private View btnRemoveImage;

    // 업로드할 이미지 상태 (선택 안 하면 null => 기존 유지)
    private Uri selectedImageUri = null;
    private byte[] selectedImageBytes = null;
    private String selectedImageMime = null;
    private String selectedImageFileName = null;

    private Dialog dialog2;

    private TokenManager tm;
    private ApiService api;
    private String clientType;
    private String verificationId;
    private final AtomicBoolean emailVerified = new AtomicBoolean(false);
    private CountDownTimer timer;

    private String originalEmail = null;
    private String originalName = null;
    private String originalTel = null;
    private String originalCompany = null;

    private boolean isDriver = false;
    private String currentRole = null;

    private static final String[] DOMAINS = new String[]{
            "gmail.com", "naver.com", "daum.net", "kakao.com", "nate.com", "직접입력"
    };

    private static final int COLOR_OK   = 0xFF2E7D32;
    private static final int COLOR_FAIL = 0xFFB00020;

    // 갤러리 런처
    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onPickImageResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit);

        verifiedPasswordFromGate = getIntent().getStringExtra("verified_pw");

        bindViews();
        setupEmailDomainUi();
        wireButtons();
        wireEmailChangeWatchers();
        wirePwRuleWatcher();
        wirePhoneFormatter();

        tm = TokenManager.get(this);
        api = ApiClient.get(this);
        clientType = DeviceInfo.getClientType();

        preloadMe(); // 사용자 정보 + (있으면) 서버 프로필 이미지 미리보기
    }

    private void bindViews() {
        btnBack = findViewById(R.id.account_back_button);
        editId  = findViewById(R.id.edit_id);

        editPassword        = findViewById(R.id.edit_password);
        editPasswordConfirm = findViewById(R.id.edit_password_confirm);

        tvRuleLen = findViewById(R.id.tv_rule_len);
        tvRuleMix = findViewById(R.id.tv_rule_mix);
        tvRuleSeq = findViewById(R.id.tv_rule_seq);
        tvPwMatch = findViewById(R.id.tv_pw_match);

        editName = findViewById(R.id.edit_name);

        // 이름 보기 전용 처리
        if (editName != null) {
            editName.setEnabled(false);
            editName.setFocusable(false);
            editName.setCursorVisible(false);
            editName.setKeyListener(null);
        }

        editCompany   = findViewById(R.id.edit_company);
        companyLayout = (editCompany != null) ? (View) editCompany.getParent() : null;

        editEmailId         = findViewById(R.id.edit_email_id);
        spinnerEmailDomain  = findViewById(R.id.spinner_email_domain);
        customDomainWrapper = findViewById(R.id.custom_domain_wrapper);
        editDomainCustom    = findViewById(R.id.editTextDomainCustom);
        btnBackToSpinner    = findViewById(R.id.btn_back_to_spinner);

        textTimer = findViewById(R.id.text_timer);
        editPhone = findViewById(R.id.edit_phone);

        btnSendCode  = findViewById(R.id.btn_send_code);
        btnVerifyCode= findViewById(R.id.btn_verify_code);
        codeInput    = findViewById(R.id.code_input);
        btnUpdate    = findViewById(R.id.btn_update);

        setVerificationUiEnabled(false);
        textTimer.setText("남은 시간: -");

        setRule(tvRuleLen, false);
        setRule(tvRuleMix, false);
        setRule(tvRuleSeq, false);
        if (tvPwMatch != null) tvPwMatch.setText("");

        if (companyLayout != null) companyLayout.setVisibility(View.GONE);

        // ===== 프로필 이미지 UI =====
        ivProfilePreview = findViewById(R.id.iv_profile_preview);
        btnPickImage     = findViewById(R.id.btn_pick_image);
        btnRemoveImage   = findViewById(R.id.btn_remove_image);

        if (btnRemoveImage != null) btnRemoveImage.setEnabled(false); // 초기엔 제거 비활성화
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

        // ===== 프로필 이미지: 선택/제거 =====
        if (btnPickImage != null) {
            btnPickImage.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                pickImageLauncher.launch(intent);
            });
        }
        if (btnRemoveImage != null) {
            btnRemoveImage.setOnClickListener(v -> {
                clearSelectedImage();
                toast("선택한 프로필 이미지를 제거했습니다.");
            });
        }
    }

    private void wireEmailChangeWatchers() {
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { onEmailInputChanged(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        editEmailId.addTextChangedListener(w);
        editDomainCustom.addTextChangedListener(w);
    }

    private void wirePwRuleWatcher() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePwRules(safe(editPassword.getText()));
                updatePwMatch();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        editPassword.addTextChangedListener(watcher);
        editPasswordConfirm.addTextChangedListener(watcher);

        updatePwRules(safe(editPassword.getText()));
        updatePwMatch();
    }

    private void onEmailInputChanged() {
        boolean changed = isEmailChanged();
        emailVerified.set(false);
        verificationId = null;
        stopTimer();
        textTimer.setText("남은 시간: -");
        setVerificationUiEnabled(changed);
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

    /** 서버 /users/me 불러와서 기본값 세팅 + 프로필 미리보기 */
    private void preloadMe() {
        String bearer = (tm.tokenType() == null ? "Bearer" : tm.tokenType()) + " " + tm.accessToken();
        api.me(bearer, clientType).enqueue(new Callback<ApiService.UserResponse>() {
            @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                ApiService.UserResponse u = res.body();

                editId.setText(safe(u.userid));

                currentRole = u.role;
                isDriver = "ROLE_DRIVER".equalsIgnoreCase(safe(u.role));

                originalName    = safe(u.username);
                originalTel     = safe(u.tel);
                originalCompany = safe(u.company);

                if (!TextUtils.isEmpty(u.username)) editName.setText(u.username);
                if (!TextUtils.isEmpty(u.tel))      editPhone.setText(u.tel);

                if (companyLayout != null) {
                    companyLayout.setVisibility(isDriver ? View.VISIBLE : View.GONE);
                }
                if (isDriver && !TextUtils.isEmpty(originalCompany)) {
                    editCompany.setText(originalCompany);
                } else if (!isDriver && editCompany != null) {
                    editCompany.setText(null);
                }

                originalEmail = safe(u.email);

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
                        spinnerEmailDomain.setSelection(DOMAINS.length - 1);
                        spinnerEmailDomain.setVisibility(View.GONE);
                        customDomainWrapper.setVisibility(View.VISIBLE);
                        editDomainCustom.setText(dom);
                    }
                } else {
                    setVerificationUiEnabled(false);
                }

                onEmailInputChanged();

                // ===== 현재 프로필 사진 미리보기 로드 =====
                if (ivProfilePreview != null) {
                    if (u.hasProfileImage) {
                        loadCurrentProfileImageIntoPreview();
                    } else {
                        ivProfilePreview.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                }
            }
            @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {}
        });
    }

    /** 서버의 현재 프로필 이미지를 받아 미리보기에 표시 (캐시 방지용 쿼리 파라미터 불필요: 바이트 직접 적용) */
    private void loadCurrentProfileImageIntoPreview() {
        String bearer = (tm.tokenType() == null ? "Bearer" : tm.tokenType()) + " " + tm.accessToken();
        api.meImage(bearer, clientType).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    ivProfilePreview.setImageResource(R.drawable.ic_profile_placeholder);
                    return;
                }
                try {
                    byte[] bytes = res.body().bytes();
                    if (bytes != null && bytes.length > 0) {
                        Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bm != null) {
                            ivProfilePreview.setImageBitmap(bm);
                        } else {
                            ivProfilePreview.setImageResource(R.drawable.ic_profile_placeholder);
                        }
                    } else {
                        ivProfilePreview.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                } catch (Exception e) {
                    ivProfilePreview.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (ivProfilePreview != null) {
                    ivProfilePreview.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
        });
    }

    private int indexOfDomain(String d) {
        for (int i=0;i<DOMAINS.length;i++) if (DOMAINS[i].equalsIgnoreCase(d)) return i;
        return -1;
    }

    // ── 이메일 인증 ─────────────────────────────────────────

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
                startTimer(5 * 60);
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

    // ── 업데이트 플로우 ────────────────────────────────────

    private void doUpdateFlow() {
        String newPw  = safe(editPassword.getText());
        String newPw2 = safe(editPasswordConfirm.getText());

        boolean wantChangePw = !TextUtils.isEmpty(newPw) || !TextUtils.isEmpty(newPw2);

        if (wantChangePw) {
            if (TextUtils.isEmpty(verifiedPasswordFromGate)) {
                toast("보안을 위해 다시 인증해 주세요.");
                finish();
                return;
            }
            if (!newPw.equals(newPw2)) { toast("비밀번호가 일치하지 않습니다"); return; }
            if (!isPwValid(newPw)) { toast("비밀번호 규칙을 확인하세요 (10~16자, 영문/숫자, 연속/키보드열 금지)"); return; }
        }

        updateProfile(changed -> {
            if (changed) toast("회원정보가 수정되었습니다.");

            if (wantChangePw) {
                changePassword(verifiedPasswordFromGate, newPw);
            } else {
                showUserEditComPopup();
            }
        });
    }

    private boolean isPwValid(String pw) {
        if (pw == null) return false;
        int len = pw.length();
        if (len < 10 || len > 16) return false;
        if (!pw.matches("^[A-Za-z0-9]+$")) return false;

        boolean hasUpper=false, hasLower=false, hasDigit=false;
        for (int i=0;i<len;i++) {
            char c = pw.charAt(i);
            if (c>='A'&&c<='Z') hasUpper=true;
            else if (c>='a'&&c<='z') hasLower=true;
            else if (c>='0'&&c<='9') hasDigit=true;
        }
        int classes = (hasUpper?1:0)+(hasLower?1:0)+(hasDigit?1:0);
        if (classes < 2) return false;

        if (hasSequentialAlphaOrDigit(pw)) return false;
        if (hasKeyboardSequence(pw)) return false;

        return true;
    }

    /** 프로필 업데이트: 이름은 전송하지 않음(보기 전용) + 이미지 멀티파트 포함(선택시에만) */
    private void updateProfile(ProfileCallback onDone) {
        boolean emailChanged = isEmailChanged();
        String email = emailChanged ? buildEmail() : null;

        // 이름은 읽기 전용이므로 전송/변경 판단에서 제외
        String tel   = safe(editPhone.getText());
        String company = isDriver && editCompany != null ? safe(editCompany.getText()) : null;

        boolean profileChanged =
                (emailChanged && !TextUtils.isEmpty(email)) ||
                        (!TextUtils.isEmpty(tel)  && !tel.equals(originalTel)) ||
                        (isDriver && company != null && !company.equals(originalCompany)) ||
                        (selectedImageBytes != null); // 이미지 선택 시에만 변경

        if (emailChanged && !emailVerified.get()) {
            toast("이메일 변경은 인증 완료 후 가능합니다");
            return;
        }

        if (!profileChanged) {
            onDone.onDone(false);
            return;
        }

        try {
            org.json.JSONObject data = new org.json.JSONObject();
            // username 미전송 (보기 전용)
            if (!TextUtils.isEmpty(tel))  data.put("tel", tel);

            if (isDriver && company != null && !company.equals(originalCompany)) {
                data.put("company", company);
            }

            if (emailChanged && !TextUtils.isEmpty(email)) {
                data.put("email", email);
                data.put("emailVerificationId", verificationId);
            }

            RequestBody dataJson = RequestBody.create(
                    data.toString().getBytes(StandardCharsets.UTF_8),
                    MediaType.parse("application/json; charset=utf-8")
            );

            MultipartBody.Part filePart = null;

            if (selectedImageBytes != null) {
                MediaType mt = MediaType.parse(
                        selectedImageMime != null ? selectedImageMime : "image/*"
                );
                RequestBody imgBody = RequestBody.create(selectedImageBytes, mt);
                // 백엔드 @Part 이름이 "file"이라고 가정 (서버와 맞추어 필요 시 변경)
                filePart = MultipartBody.Part.createFormData(
                        "file",
                        (selectedImageFileName == null ? "profile.jpg" : selectedImageFileName),
                        imgBody
                );
            }

            String bearer = (tm.tokenType() == null ? "Bearer" : tm.tokenType()) + " " + tm.accessToken();
            api.updateMe(bearer, clientType, dataJson, filePart)
                    .enqueue(new Callback<ApiService.UserResponse>() {
                        @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                            if (res.isSuccessful() && res.body() != null) {
                                originalTel  = TextUtils.isEmpty(tel)  ? originalTel  : tel;
                                if (isDriver && company != null) originalCompany = company;

                                if (emailChanged && email != null) {
                                    originalEmail = email;
                                    emailVerified.set(false);
                                    verificationId = null;
                                    stopTimer();
                                    textTimer.setText("남은 시간: -");
                                    setVerificationUiEnabled(false);
                                }

                                if (selectedImageBytes != null) {
                                    // 업로드 성공 후: 서버 이미지로 교체됐으니 로컬 선택 상태 리셋 + 서버에서 다시 로드
                                    selectedImageBytes = null;
                                    selectedImageUri = null;
                                    selectedImageFileName = null;
                                    selectedImageMime = null;
                                    if (btnRemoveImage != null) btnRemoveImage.setEnabled(false);
                                    loadCurrentProfileImageIntoPreview();
                                }
                                onDone.onDone(true);
                            } else if (res.code() == 409) {
                                toast("이미 사용 중인 이메일입니다");
                            } else if (res.code() == 403) {
                                toast("회사명 변경은 드라이버만 가능합니다");
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

    private void changePassword(String currentPw, String newPw) {
        String bearer = (tm.tokenType() == null ? "Bearer" : tm.tokenType()) + " " + tm.accessToken();
        ApiService.ChangePasswordRequest body = new ApiService.ChangePasswordRequest(currentPw, newPw);

        api.changePassword(bearer, clientType, body).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> res) {
                if (res.isSuccessful()) {
                    silentReloginWithNewPassword(newPw);
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

    private void silentReloginWithNewPassword(String newPw) {
        String userid = safe(editId.getText());
        String deviceId = tm.deviceId();

        ApiService.AuthRequest req = new ApiService.AuthRequest(
                userid, newPw, clientType, deviceId
        );

        api.login(req).enqueue(new Callback<ApiService.AuthResponse>() {
            @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                if (res.isSuccessful() && res.body() != null) {
                    ApiService.AuthResponse a = res.body();
                    tm.saveLogin(a.accessToken, a.refreshToken,
                            (a.tokenType == null ? "Bearer" : a.tokenType),
                            a.role);
                    toast("비밀번호가 변경되었습니다.");
                    goMain();
                } else {
                    toast("비밀번호는 변경됐지만 세션 갱신에 실패했습니다. 다시 로그인해 주세요.");
                    goLogin();
                }
            }

            @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                toast("세션 갱신 실패: " + t.getMessage());
                goLogin();
            }
        });
    }

    private void goMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void goLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

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
            // 수정됨을 상위에 알림 (MainActivity가 onResume에서 loadProfile 호출하도록)
            setResult(RESULT_OK);
            finish();
        });
        dialog2.show();
    }

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

    private void updatePwRules(String pw) {
        if (TextUtils.isEmpty(pw)) {
            setRule(tvRuleLen, false);
            setRule(tvRuleMix, false);
            setRule(tvRuleSeq, false);
            return;
        }
        boolean lenOk = pw.length() >= 10 && pw.length() <= 16 && pw.matches("^[A-Za-z0-9]+$");
        setRule(tvRuleLen, lenOk);

        boolean hasUpper=false, hasLower=false, hasDigit=false;
        for (int i=0;i<pw.length();i++) {
            char c = pw.charAt(i);
            if (c>='A'&&c<='Z') hasUpper=true;
            else if (c>='a'&&c<='z') hasLower=true;
            else if (c>='0'&&c<='9') hasDigit=true;
        }
        int classes = (hasUpper?1:0)+(hasLower?1:0)+(hasDigit?1:0);
        setRule(tvRuleMix, classes >= 2);

        boolean badSeq = hasSequentialAlphaOrDigit(pw) || hasKeyboardSequence(pw);
        setRule(tvRuleSeq, !badSeq);
    }

    private void updatePwMatch() {
        if (tvPwMatch == null) return;
        String a = safe(editPassword.getText());
        String b = safe(editPasswordConfirm.getText());

        if (TextUtils.isEmpty(a) && TextUtils.isEmpty(b)) {
            tvPwMatch.setText("");
            return;
        }
        if (!TextUtils.isEmpty(b) && a.equals(b)) {
            tvPwMatch.setText("일치합니다");
            tvPwMatch.setTextColor(COLOR_OK);
        } else {
            tvPwMatch.setText("일치하지 않습니다");
            tvPwMatch.setTextColor(COLOR_FAIL);
        }
    }

    private void setRule(TextView tv, boolean ok) {
        tv.setTextColor(ok ? COLOR_OK : COLOR_FAIL);
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
                "qwertyuiop","asdfghjkl","zxcvbnm","1234567890","0987654321"
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

    private void wirePhoneFormatter() {
        if (editPhone == null) return;
        editPhone.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String digits = s.toString().replaceAll("[^0-9]", "");
                StringBuilder formatted = new StringBuilder();

                if (digits.length() <= 3) {
                    formatted.append(digits);
                } else if (digits.length() <= 7) {
                    formatted.append(digits, 0, 3).append("-").append(digits.substring(3));
                } else if (digits.length() <= 11) {
                    formatted.append(digits, 0, 3).append("-").append(digits, 3, 7).append("-").append(digits.substring(7));
                } else {
                    formatted.append(digits, 0, 3).append("-").append(digits, 3, 7).append("-").append(digits, 7, 11);
                }

                editPhone.setText(formatted.toString());
                editPhone.setSelection(editPhone.getText().length());
                isFormatting = false;
            }
        });
    }

    // ===== 갤러리 결과 처리 =====
    private void onPickImageResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
        Uri uri = result.getData().getData();
        if (uri == null) return;

        try {
            ContentResolver cr = getContentResolver();
            String mime = cr.getType(uri);
            String name = queryDisplayName(uri);
            if (name == null) name = "profile.jpg";

            // 바이트로 읽기
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (InputStream is = cr.openInputStream(uri)) {
                if (is == null) { toast("이미지를 불러올 수 없습니다."); return; }
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            }
            byte[] bytes = bos.toByteArray();
            if (bytes.length == 0) { toast("이미지 파일이 비어 있습니다."); return; }

            selectedImageUri = uri;
            selectedImageBytes = bytes;
            selectedImageMime = (mime == null ? "image/*" : mime);
            selectedImageFileName = name;

            // 미리보기 (로컬 선택 이미지)
            if (ivProfilePreview != null) {
                ivProfilePreview.setImageURI(null); // 캐시 방지
                ivProfilePreview.setImageURI(uri);
            }
            if (btnRemoveImage != null) btnRemoveImage.setEnabled(true);

            toast("프로필 이미지를 선택했습니다.");

        } catch (Exception e) {
            toast("이미지 처리 오류: " + e.getMessage());
        }
    }

    private void clearSelectedImage() {
        selectedImageUri = null;
        selectedImageBytes = null;
        selectedImageMime = null;
        selectedImageFileName = null;
        if (ivProfilePreview != null) {
            ivProfilePreview.setImageResource(R.drawable.ic_profile_placeholder);
        }
        if (btnRemoveImage != null) btnRemoveImage.setEnabled(false);
    }

    @Nullable
    private String queryDisplayName(Uri uri) {
        String name = null;
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignore) {
        } finally {
            if (c != null) c.close();
        }
        return name;
    }

    private interface ProfileCallback {
        void onDone(boolean changed);
    }
}
