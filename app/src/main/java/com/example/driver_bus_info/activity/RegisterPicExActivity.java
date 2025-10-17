package com.example.driver_bus_info.activity;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.ui.Anim;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RegisterPicExActivity extends AppCompatActivity {

    private Button      buttonSubmitNext;   // 다음(=바로 촬영)
    private ImageButton registerButtonBack; // 뒤로가기

    // 빠른 연타 방지
    private boolean navigating = false;

    // 촬영 결과 저장용 Uri/경로
    private Uri photoUri = null;
    private String photoPath = null;

    // 권한 요청 런처 & 촬영 런처
    private ActivityResultLauncher<String> requestCameraPermission;
    private ActivityResultLauncher<Uri>    takePictureLauncher;

    private static final String STATE_PHOTO_URI  = "state_photo_uri";
    private static final String STATE_PHOTO_PATH = "state_photo_path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_pic_ex);

        buttonSubmitNext   = findViewById(R.id.buttonSubmitnext);
        registerButtonBack = findViewById(R.id.registerButtonBack);

        // --- Activity Result 준비 ---
        requestCameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCameraCapture();
                    } else {
                        Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                        navigating = false;
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean success) {
                        if (success != null && success) {
                            goNextWithPhoto();
                        } else {
                            Toast.makeText(RegisterPicExActivity.this, "촬영이 취소되었습니다.", Toast.LENGTH_SHORT).show();
                            navigating = false;
                        }
                    }
                }
        );

        // --- 상태 복원 ---
        if (savedInstanceState != null) {
            String savedUri  = savedInstanceState.getString(STATE_PHOTO_URI);
            String savedPath = savedInstanceState.getString(STATE_PHOTO_PATH);
            if (savedUri != null) photoUri = Uri.parse(savedUri);
            photoPath = savedPath;
        }

        // === 등장 애니메이션: 첫 진입 때만 ===
        if (savedInstanceState == null) {
            View topBar = findViewById(R.id.top_bar);
            View title  = findViewById(R.id.textTitle);
            if (topBar != null)  Anim.slideDownIn(topBar, 24f, 0);
            if (title != null)   Anim.slideDownIn(title, 24f, 20);
            if (registerButtonBack != null) Anim.slideDownIn(registerButtonBack, 24f, 20);
            Anim.intro(this, R.id.textView7, R.id.imageView2, R.id.textView8, R.id.buttonSubmitnext);
        }

        // 다음 → 바로 카메라 촬영
        if (buttonSubmitNext != null) {
            buttonSubmitNext.setOnClickListener(v -> {
                if (navigating) return;
                navigating = true;
                Anim.bump(v);
                ensurePermissionAndCapture();
            });
        }

        // 상단 뒤로 → 로그인(또는 이전 단계)
        if (registerButtonBack != null) {
            registerButtonBack.setOnClickListener(v -> navigateBackToRegister());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (photoUri  != null) out.putString(STATE_PHOTO_URI,  photoUri.toString());
        if (photoPath != null) out.putString(STATE_PHOTO_PATH, photoPath);
    }

    @Override
    public void onBackPressed() {
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

    // 권한 확인 후 촬영 시작
    private void ensurePermissionAndCapture() {
        // Android 13- : CAMERA 권한 필요(13+도 호환성 위해 요청 권장)
        requestCameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void startCameraCapture() {
        try {
            File imageFile = createTempImageFile();
            photoPath = imageFile.getAbsolutePath();
            photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imageFile
            );
            takePictureLauncher.launch(photoUri);
        } catch (IOException e) {
            Toast.makeText(this, "임시 파일 생성 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            navigating = false;
        }
    }

    private File createTempImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName  = "DL_" + timeStamp + "_";
        File storageDir  = getExternalFilesDir("camera"); // 앱 전용 디렉토리
        if (storageDir != null && !storageDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            storageDir.mkdirs();
        }
        return File.createTempFile(fileName, ".jpg", storageDir);
    }

    private void goNextWithPhoto() {
        if (photoUri == null) {
            Toast.makeText(this, "촬영 결과가 없습니다.", Toast.LENGTH_SHORT).show();
            navigating = false;
            return;
        }
        Intent i = new Intent(this, RegisterCheckActivity.class);
        i.putExtra(RegisterCheckActivity.EXTRA_LICENSE_PHOTO_URI,  photoUri.toString());
        i.putExtra("licensePhotoPath", photoPath); // 필요 시 경로 활용
        startActivity(i);
        Anim.fadeTransition(this);
        finish();
    }
}
