package com.example.driver_bus_info.activity;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InquiryComposeActivity extends AppCompatActivity {

    private View btnBack;
    private TextView btnAttach, btnSubmit;
    private LinearLayout chipContainer;

    private TextInputLayout tilName, tilTitle, tilContent;
    private TextInputEditText etName, etTitle, etContent;

    private TextInputLayout tilEmailLocal, tilEmailDomain, tilEmailDomainDD;
    private TextInputEditText etEmailLocal, etEmailDomain;
    private MaterialAutoCompleteTextView ddEmailDomain;

    private String clientType;
    private MaterialSwitch swSecret;
    private TextInputLayout tilPassword;
    private TextInputEditText etPassword;

    @Nullable private String meUserid = null;
    @Nullable private String meEmail  = null;
    @Nullable private String meRole   = null;

    private TokenManager tm;

    private final List<Uri> selectedUris = new ArrayList<>();
    private final ActivityResultLauncher<String[]> pickFiles =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(),
                    uris -> {
                        if (uris == null || uris.isEmpty()) return;
                        selectedUris.clear();
                        selectedUris.addAll(uris);
                        renderChips();
                    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry_compose);

        clientType = DeviceInfo.getClientType();
        tm = TokenManager.get(this);

        btnBack       = findViewById(R.id.notice_back_button);
        btnAttach     = findViewById(R.id.btn_attach);
        btnSubmit     = findViewById(R.id.btn_submit);
        chipContainer = findViewById(R.id.chip_container);

        tilName   = findViewById(R.id.til_name);
        tilTitle  = findViewById(R.id.til_title);
        tilContent= findViewById(R.id.til_content);
        etName    = findViewById(R.id.et_name);
        etTitle   = findViewById(R.id.et_title);
        etContent = findViewById(R.id.et_content);

        tilEmailLocal   = findViewById(R.id.til_email_local);
        tilEmailDomain  = findViewById(R.id.til_email_domain);
        tilEmailDomainDD= findViewById(R.id.til_email_domain_dd);
        etEmailLocal    = findViewById(R.id.et_email_local);
        etEmailDomain   = findViewById(R.id.et_email_domain);
        ddEmailDomain   = findViewById(R.id.dd_email_domain);

        swSecret   = findViewById(R.id.sw_secret);
        tilPassword= findViewById(R.id.til_password);
        etPassword = findViewById(R.id.et_password);

        btnBack.setOnClickListener(v -> finish());
        btnAttach.setOnClickListener(v -> pickFiles.launch(new String[]{"*/*"}));
        btnSubmit.setOnClickListener(v -> submit());

        setupDomainDropdown();

        swSecret.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                tilPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        tilPassword.setVisibility(View.GONE);

        // ===== 로그인 상태라면 이름/이메일만 미리 채우고 읽기 전용으로 =====
        String access = tm.accessToken();
        if (!TextUtils.isEmpty(access)) {
            final String bearer = (tm.tokenType() != null ? tm.tokenType() : "Bearer") + " " + access;
            ApiClient.get(getApplicationContext())
                    .me(bearer, clientType)
                    .enqueue(new Callback<ApiService.UserResponse>() {
                        @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                            if (res.isSuccessful() && res.body()!=null) {
                                ApiService.UserResponse me = res.body();
                                meUserid = me.userid; meEmail = me.email; meRole = me.role;

                                // 이름 자동 입력 (username > userid)
                                String nameAuto = (me.username != null && !me.username.trim().isEmpty())
                                        ? me.username : (me.userid != null ? me.userid : "");
                                if (!TextUtils.isEmpty(nameAuto)) {
                                    etName.setText(nameAuto);
                                    etName.setEnabled(false); // 이름 수정 불가
                                }

                                // 이메일 자동 입력
                                if (!TextUtils.isEmpty(me.email) && me.email.contains("@")) {
                                    String[] a = me.email.split("@", 2);
                                    etEmailLocal.setText(a[0]);
                                    etEmailLocal.setEnabled(false); // 이메일 앞부분 수정 불가
                                    etEmailDomain.setText(a[1]);
                                    etEmailDomain.setEnabled(false); // 도메인 수정 불가
                                    ddEmailDomain.setEnabled(false); // 드롭다운 비활성화
                                }
                            }
                        }
                        @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) { /* no-op */ }
                    });
        }
    }

    private void setupDomainDropdown() {
        String[] domains = new String[]{
                "gmail.com", "naver.com", "daum.net", "nate.com",
                "kakao.com", "hotmail.com", "outlook.com", "직접 입력"
        };
        ddEmailDomain.setSimpleItems(domains);
        ddEmailDomain.setOnItemClickListener((parent, view, position, id) -> {
            String sel = (String) parent.getItemAtPosition(position);
            if ("직접 입력".equals(sel)) {
                etEmailDomain.setEnabled(true);
                etEmailDomain.setText("");
                etEmailDomain.requestFocus();
            } else {
                etEmailDomain.setText(sel);
                etEmailDomain.setEnabled(false);
            }
        });
        etEmailDomain.setEnabled(true);
    }

    private void renderChips() {
        chipContainer.removeAllViews();
        for (Uri u : selectedUris) {
            View chip = getLayoutInflater().inflate(R.layout.view_file_chip, chipContainer, false);
            TextView tv = chip.findViewById(R.id.chip_text);
            tv.setText(displayName(u));
            View close = chip.findViewById(R.id.chip_close);
            close.setOnClickListener(v -> {
                selectedUris.remove(u);
                renderChips();
            });
            chipContainer.addView(chip);
        }
        chipContainer.setVisibility(selectedUris.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignore) {}
        if (TextUtils.isEmpty(name)) name = uri.getLastPathSegment();
        return name == null ? "file" : name;
    }

    private void submit() {
        tilName.setError(null);
        tilEmailLocal.setError(null);
        tilEmailDomain.setError(null);
        tilTitle.setError(null);
        tilContent.setError(null);
        tilPassword.setError(null);

        String name        = textOf(etName);
        String emailLocal  = textOf(etEmailLocal);
        String emailDomain = textOf(etEmailDomain);
        String title       = textOf(etTitle);
        String content     = textOf(etContent);
        boolean secret     = swSecret.isChecked();
        String password    = textOf(etPassword);

        if (TextUtils.isEmpty(name))        { tilName.setError("이름을 입력하세요"); return; }
        if (TextUtils.isEmpty(emailLocal))  { tilEmailLocal.setError("이메일 아이디를 입력하세요"); return; }
        if (TextUtils.isEmpty(emailDomain)) { tilEmailDomain.setError("도메인을 입력/선택하세요"); return; }

        String email = emailLocal + "@" + emailDomain;
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmailDomain.setError("올바른 이메일 형식이 아닙니다"); return;
        }
        if (TextUtils.isEmpty(title))       { tilTitle.setError("제목을 입력하세요"); return; }
        if (TextUtils.isEmpty(content))     { tilContent.setError("내용을 입력하세요"); return; }
        if (secret) {
            if (TextUtils.isEmpty(password) || password.length()<4 || password.length()>64) {
                tilPassword.setError("비밀번호는 4~64자입니다"); return;
            }
        }

        String xUserId = meUserid;
        String xRole   = meRole;
        String xEmail  = email;

        RequestBody rbTitle    = toText(title);
        RequestBody rbContent  = toText(content);
        RequestBody rbName     = toText(name);
        RequestBody rbSecret   = toText(secret ? "true" : "false");
        RequestBody rbPassword = secret ? toText(password) : null;

        List<MultipartBody.Part> fileParts = new ArrayList<>();
        for (Uri u : selectedUris) {
            MultipartBody.Part p = toFilePart("files", u);
            if (p != null) fileParts.add(p);
        }

        setSending(true);
        ApiClient.get(getApplicationContext())
                .createInquiry(xUserId, xEmail, xRole,
                        rbTitle, rbContent, rbName, rbSecret, rbPassword, fileParts)
                .enqueue(new Callback<ApiService.InquiryResp>() {
                    @Override public void onResponse(Call<ApiService.InquiryResp> call, Response<ApiService.InquiryResp> res) {
                        setSending(false);
                        if (res.isSuccessful() && res.body()!=null) {
                            Toast.makeText(InquiryComposeActivity.this, "문의가 접수되었습니다.", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        String msg = "전송 실패 ("+res.code()+")";
                        try { if (res.errorBody()!=null) msg += " " + res.errorBody().string(); } catch (Exception ignore) {}
                        Toast.makeText(InquiryComposeActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                    @Override public void onFailure(Call<ApiService.InquiryResp> call, Throwable t) {
                        setSending(false);
                        Toast.makeText(InquiryComposeActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setSending(boolean sending) {
        btnSubmit.setEnabled(!sending);
        btnAttach.setEnabled(!sending);
        View p = findViewById(R.id.progress);
        if (p != null) p.setVisibility(sending ? View.VISIBLE : View.GONE);
    }

    private String textOf(TextInputEditText e) { return e.getText()==null ? "" : e.getText().toString().trim(); }
    private static RequestBody toText(String v) { return RequestBody.create(MultipartBody.FORM, v == null ? "" : v); }

    private MultipartBody.Part toFilePart(String name, Uri uri) {
        try {
            ContentResolver cr = getContentResolver();
            String fn = displayName(uri);
            String rawMime = cr.getType(uri);
            String safe = safeMime(rawMime);

            File cache = new File(getCacheDir(), "inq_" + System.nanoTime() + "_" + fn);
            try (InputStream in = cr.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(cache)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            RequestBody body = RequestBody.create(MediaType.parse(safe), cache);
            return MultipartBody.Part.createFormData(name, fn, body);
        } catch (Exception e) {
            Toast.makeText(this, "파일 처리 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private static String safeMime(@Nullable String raw) {
        final String fallback = "application/octet-stream";
        if (raw == null || raw.trim().isEmpty()) return fallback;
        String s = raw.trim();
        int sc = s.indexOf(';'); if (sc > 0) s = s.substring(0, sc);
        s = s.toLowerCase();
        if (!s.matches("[a-z0-9.+-]+/[a-z0-9.+-]+")) return fallback;
        return s;
    }

    private String textOf(TextView t) { return t.getText()==null ? "" : t.getText().toString().trim(); }
}
