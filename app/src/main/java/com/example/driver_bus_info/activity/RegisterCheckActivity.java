package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.ui.Anim;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class RegisterCheckActivity extends AppCompatActivity {

    public static final String EXTRA_LICENSE_PHOTO_URI = "licensePhotoUri";
    public static final String EXTRA_LICENSE_NUMBER    = "licenseNumber";
    public static final String EXTRA_ACQUIRED_DATE     = "acquiredDate";
    public static final String EXTRA_NAME              = "name";
    public static final String EXTRA_BIRTH             = "birth";

    private MaterialButton buttonRegisterSubmit;
    private ImageButton registerButtonBack;
    private MaterialButton bottomBackButton;

    // 입력칸 (수정 가능)
    private TextInputEditText etName, etBirth, etLicenseCode, etLicenseDay;

    // 사진
    private MaterialButton btnPickImage;
    private MaterialButton btnRetakeCamera;
    private androidx.appcompat.widget.AppCompatImageView ivPreview;

    private boolean navigating = false;

    // 현재 선택된(혹은 전달된) 면허 사진 URI
    private Uri currentPhotoUri = null;

    // 갤러리에서 사진 선택
    private ActivityResultLauncher<String> pickImageLauncher;

    // 포맷팅 루프 방지 플래그
    private boolean isFormattingLicense = false;

    // 날짜 포맷(표시는 yyyy-MM-dd)
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);

    // 패턴(검증용)
    private static final String PATTERN_OLD = "^\\d-\\d{2}-\\d{6}$";    // 구: 1-2-6 예) 1-23-456789
    private static final String PATTERN_NEW = "^\\d{2}-\\d{2}-\\d{5}$";  // 신: 2-2-5 예) 12-34-56789

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_check);

        // 뷰 바인딩
        buttonRegisterSubmit = findViewById(R.id.buttonRegisterSubmit);
        registerButtonBack   = findViewById(R.id.registerButtonBack);
        bottomBackButton     = findViewById(R.id.buttonRegisterBack);

        etName        = findViewById(R.id.editTextName);
        etBirth       = findViewById(R.id.editTextBirth);
        etLicenseCode = findViewById(R.id.editTextlicenseCode);
        etLicenseDay  = findViewById(R.id.editTextlicenseDay);

        btnPickImage    = findViewById(R.id.btnPickImage);
        btnRetakeCamera = findViewById(R.id.btnRetakeCamera);
        ivPreview       = findViewById(R.id.ivLicensePreview);

        // 이전 화면에서 전달된 값 수신
        Intent fromPic = getIntent();
        if (fromPic != null) {
            setIfNonNull(etName,        fromPic.getStringExtra(EXTRA_NAME));
            setIfNonNull(etBirth,       fromPic.getStringExtra(EXTRA_BIRTH));
            setIfNonNull(etLicenseCode, fromPic.getStringExtra(EXTRA_LICENSE_NUMBER));
            setIfNonNull(etLicenseDay,  fromPic.getStringExtra(EXTRA_ACQUIRED_DATE));

            String uriStr = fromPic.getStringExtra(EXTRA_LICENSE_PHOTO_URI);
            if (!TextUtils.isEmpty(uriStr)) {
                try {
                    currentPhotoUri = Uri.parse(uriStr);
                    ivPreview.setImageURI(currentPhotoUri);
                } catch (Exception ignore) {
                    currentPhotoUri = null;
                }
            }
        }

        // 날짜 필드: 키보드 입력 막고 "선택만"
        setupDatePickers();

        // 면허번호 자동 하이픈(구/신 모두 허용, 자동 추정: 기본 신 포맷)
        wireLicenseFormatter();

        // 갤러리 선택 런처 준비
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) {
                        toast("선택이 취소되었습니다.");
                        return;
                    }
                    currentPhotoUri = uri;
                    ivPreview.setImageURI(currentPhotoUri);
                }
        );

        // 사진 선택(갤러리)
        if (btnPickImage != null) {
            btnPickImage.setOnClickListener(v -> {
                Anim.bump(v);
                pickImageLauncher.launch("image/*");
            });
        }

        // 다시 촬영(이전 화면으로 이동)
        if (btnRetakeCamera != null) {
            btnRetakeCamera.setOnClickListener(v -> {
                if (navigating) return;
                navigating = true;
                Anim.bump(v);
                startActivity(new Intent(this, RegisterPicExActivity.class));
                Anim.fadeTransition(this);
                finish();
            });
        }

        // 제출 → RegisterActivity로 이동 (입력값 + 사진URI 전달)
        if (buttonRegisterSubmit != null) {
            buttonRegisterSubmit.setOnClickListener(v -> {
                if (navigating) return;
                navigating = true;
                Anim.bump(v);

                // 필수값 가드
                if (isEmpty(etName) || isEmpty(etBirth) || isEmpty(etLicenseCode) || isEmpty(etLicenseDay)) {
                    navigating = false;
                    toast("이름/생년월일/자격증번호/취득일을 모두 입력하세요.");
                    return;
                }

                // 면허번호 최종 정규화/검증
                String licenseFormatted = normalizeLicenseForSubmit(textOrNull(etLicenseCode));
                if (licenseFormatted == null) {
                    navigating = false;
                    toast("자격증번호 형식이 올바르지 않습니다. (구: 0-00-000000 / 신: 00-00-00000)");
                    return;
                }

                Intent toRegister = new Intent(this, RegisterActivity.class);
                toRegister.putExtra(EXTRA_NAME,           textOrNull(etName));
                toRegister.putExtra(EXTRA_BIRTH,          textOrNull(etBirth));          // yyyy-MM-dd (선택기 결과)
                toRegister.putExtra(EXTRA_LICENSE_NUMBER, licenseFormatted);             // 구/신 둘 다 허용
                toRegister.putExtra(EXTRA_ACQUIRED_DATE,  textOrNull(etLicenseDay));     // yyyy-MM-dd (선택기 결과)

                if (currentPhotoUri != null) {
                    toRegister.putExtra(EXTRA_LICENSE_PHOTO_URI, currentPhotoUri.toString());
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
        startActivity(new Intent(this, RegisterPicExActivity.class));
        Anim.fadeTransition(this);
        finish();
    }

    private void setIfNonNull(TextInputEditText et, String v) {
        if (et != null && v != null) et.setText(v);
    }

    private String textOrNull(TextInputEditText et) {
        if (et == null) return null;
        CharSequence cs = et.getText();
        return cs == null ? null : cs.toString().trim();
    }

    private boolean isEmpty(TextInputEditText et) {
        String s = textOrNull(et);
        return s == null || s.isEmpty();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // -------------------- 날짜: 선택 전용 --------------------

    private void setupDatePickers() {
        // 키보드 입력 막고 클릭 시 선택기 표시
        makePickerOnly(etBirth);
        makePickerOnly(etLicenseDay);

        if (etBirth != null) {
            etBirth.setOnClickListener(v -> showDatePicker(etBirth, /*maxToday=*/true));
        }
        if (etLicenseDay != null) {
            etLicenseDay.setOnClickListener(v -> showDatePicker(etLicenseDay, /*maxToday=*/true));
        }
    }

    private void makePickerOnly(TextInputEditText et) {
        if (et == null) return;
        try {
            et.setShowSoftInputOnFocus(false);
        } catch (Throwable ignore) {}
        et.setCursorVisible(false);
        et.setFocusable(true);
        et.setFocusableInTouchMode(true);
    }

    private void showDatePicker(TextInputEditText target, boolean maxToday) {
        CalendarConstraints.Builder constraints = new CalendarConstraints.Builder();
        if (maxToday) {
            constraints.setEnd(Calendar.getInstance().getTimeInMillis());
        }

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder
                .datePicker()
                .setTitleText("날짜 선택")
                .setCalendarConstraints(constraints.build())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            try {
                // selection: UTC millis
                Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                utc.setTimeInMillis(selection);

                // 로컬표시 yyyy-MM-dd
                Calendar local = Calendar.getInstance();
                local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
                String s = dateFmt.format(local.getTime());
                setTextEnd(target, s);
            } catch (Exception ignore) {}
        });

        picker.show(getSupportFragmentManager(), "date_picker_" + target.getId());
    }

    private void setTextEnd(TextInputEditText et, String newText) {
        String old = et.getText() == null ? "" : et.getText().toString();
        if (TextUtils.equals(old, newText)) return;
        et.setText(newText);
        try { et.setSelection(newText.length()); } catch (Exception ignore) {}
    }

    // -------------------- 면허번호: 구/신 모두 허용 --------------------

    private void wireLicenseFormatter() {
        if (etLicenseCode == null) return;
        etLicenseCode.addTextChangedListener(new SimpleTW() {
            @Override public void afterTextChanged(Editable s) {
                if (isFormattingLicense) return;
                isFormattingLicense = true;

                String formatted = autoFormatLicenseSmart(s.toString());
                setTextKeepCursor(etLicenseCode, formatted);

                isFormattingLicense = false;
            }
        });
    }

    /**
     * 입력 편의용 자동 포맷:
     * - 사용자가 직접 하이픈 배치하면 그대로 유지 (구/신 모두 통과)
     * - 하이픈 없이 숫자만 입력하면 "신(2-2-5)" 기준으로 점진 포맷
     * - 최대 9자리 숫자만 허용
     */
    private String autoFormatLicenseSmart(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();

        // 이미 구/신 정규식에 맞으면 건드리지 않음
        if (trimmed.matches(PATTERN_OLD) || trimmed.matches(PATTERN_NEW)) {
            return trimmed;
        }

        // 숫자만 뽑아서 신(2-2-5) 점진 포맷
        String d = trimmed.replaceAll("[^0-9]", "");
        if (d.length() > 9) d = d.substring(0, 9);

        if (d.length() <= 2) return d;                              // 0~2
        if (d.length() <= 4) return d.substring(0,2) + "-" + d.substring(2);          // 3~4 -> 00-xx
        if (d.length() <= 9) return d.substring(0,2) + "-" + d.substring(2,4) + "-" + d.substring(4); // 5~9 -> 00-00-xxxxx
        return d;
    }

    /**
     * 제출용 정규화/검증:
     * - 구: 1-2-6  (^\d-\d{2}-\d{6}$)
     * - 신: 2-2-5  (^\d{2}-\d{2}-\d{5}$)
     * - 하이픈 없이 9자리면 기본 "신"으로 조립
     * - 둘 다 아니면 null
     */
    private String normalizeLicenseForSubmit(String input) {
        if (TextUtils.isEmpty(input)) return null;
        String s = input.trim();

        if (s.matches(PATTERN_OLD) || s.matches(PATTERN_NEW)) return s;

        String d = s.replaceAll("[^0-9]", "");
        if (d.length() != 9) return null;

        // 기본: 신(2-2-5)로 조립
        String candidate = d.substring(0,2) + "-" + d.substring(2,4) + "-" + d.substring(4);
        return candidate.matches(PATTERN_NEW) ? candidate : null;
    }

    /** 커서 위치 최대한 유지해서 setText */
    private void setTextKeepCursor(TextInputEditText et, String newText) {
        int oldPos = et.getSelectionStart();
        String old = et.getText() == null ? "" : et.getText().toString();
        et.setText(newText);
        int delta = newText.length() - old.length();
        int newPos = Math.max(0, Math.min(newText.length(), oldPos + delta));
        try {
            et.setSelection(newPos);
        } catch (Exception ignore) {
            et.setSelection(newText.length());
        }
    }

    // 간단 TextWatcher
    private static abstract class SimpleTW implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
