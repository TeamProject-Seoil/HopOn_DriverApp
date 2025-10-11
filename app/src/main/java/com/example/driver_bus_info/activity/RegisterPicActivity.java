package com.example.driver_bus_info.activity;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
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

public class RegisterPicActivity extends AppCompatActivity {

    private ImageButton imageButton2;       // 촬영/다음
    private ImageButton registerButtonBack; // 뒤로가기

    private boolean navigating = false;     // 연타 방지

    // 촬영 결과 저장용 Uri
    private Uri photoUri = null;
    private String photoPath = null;

    // 권한 요청 런처
    private ActivityResultLauncher<String> requestCameraPermission;
    // 촬영 런처(TakePicture는 미리 준비한 Uri 필요)
    private ActivityResultLauncher<Uri> takePictureLauncher;

    private static final String STATE_PHOTO_URI = "state_photo_uri";
    private static final String STATE_PHOTO_PATH = "state_photo_path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_pic);

        imageButton2       = findViewById(R.id.imageButton2);
        registerButtonBack = findViewById(R.id.registerButtonBack);

        // --- Activity Result 준비 ---
        requestCameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCameraCapture();
                    } else {
                        Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean success) {
                        if (success != null && success) {
                            // 촬영 성공 → 다음 화면으로 Uri 전달
                            goNextWithPhoto();
                        } else {
                            Toast.makeText(RegisterPicActivity.this, "촬영이 취소되었습니다.", Toast.LENGTH_SHORT).show();
                            navigating = false;
                        }
                    }
                }
        );

        // --- 상태 복원 ---
        if (savedInstanceState != null) {
            String savedUri = savedInstanceState.getString(STATE_PHOTO_URI);
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
            Anim.intro(this, R.id.textView7, R.id.imageView2, R.id.imageButton2);
        }

        // 촬영/다음 → 카메라 실행
        if (imageButton2 != null) {
            imageButton2.setOnClickListener(v -> {
                if (navigating) return;
                navigating = true;
                Anim.bump(v);
                ensurePermissionAndCapture();
            });
        }

        // 상단 뒤로 → 이전 안내 화면
        if (registerButtonBack != null) {
            registerButtonBack.setOnClickListener(v -> navigateBackToEx());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (photoUri != null) out.putString(STATE_PHOTO_URI, photoUri.toString());
        if (photoPath != null) out.putString(STATE_PHOTO_PATH, photoPath);
    }

    @Override
    public void onBackPressed() {
        navigateBackToEx();
    }

    private void navigateBackToEx() {
        if (navigating) return;
        navigating = true;
        if (registerButtonBack != null) Anim.bump(registerButtonBack);
        startActivity(new Intent(this, RegisterPicExActivity.class));
        Anim.fadeTransition(this);
        finish();
    }

    // 권한 확인 후 촬영 시작
    private void ensurePermissionAndCapture() {
        // Android 13- : CAMERA 권한 필요. (Android 13+도 카메라 인텐트는 권장상 권한 요청)
        requestCameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void startCameraCapture() {
        try {
            // 임시 파일 준비
            File imageFile = createTempImageFile();
            photoPath = imageFile.getAbsolutePath();
            photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imageFile
            );
            // 카메라 앱에 파일 Uri 권한 부여는 FileProvider가 처리함
            takePictureLauncher.launch(photoUri);
        } catch (IOException e) {
            Toast.makeText(this, "임시 파일 생성 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            navigating = false;
        }
    }

    private File createTempImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "DL_" + timeStamp + "_";
        // 앱 전용 캐시/그림 디렉토리 (자동 정리 대상)
        File storageDir = getExternalFilesDir("camera");
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
        // 다음 화면에서 표시/검증/추출(정보 입력)할 수 있도록 Uri 전달
        i.putExtra("licensePhotoUri", photoUri.toString());
        i.putExtra("licensePhotoPath", photoPath); // 필요하면 파일 경로도 전달
        startActivity(i);
        Anim.fadeTransition(this);
        finish();
    }
}
