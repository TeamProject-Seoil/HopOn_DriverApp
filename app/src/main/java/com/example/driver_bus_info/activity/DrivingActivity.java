package com.example.driver_bus_info.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 운행 현황 화면
 * - 승객 현황/지연 : 예약 연동 예정 (현 UI 유지)
 * - 운행 종료 : 서버 API 호출
 * - 위치 하트비트 : requestLocationUpdates 콜백에서 3초 간격으로 전송
 * - 정차 신호 데모 : 색상 토글 (실연동 시 이벤트 연결)
 */
public class DrivingActivity extends AppCompatActivity {

    // ==== 인텐트/프리퍼런스 키 ====
    private static final String PREF = "driver_prefs";
    private static final String K_OPERATION_ID = "sel_operation_id";
    private static final String K_PLATE_NO     = "sel_plate_no";

    // ==== 서버 ====
    private ApiService api;

    // ==== 상단 버스 정보 ====
    private TextView tvBusNumber;    // 상단 큰 글씨: 노선 ID
    private TextView tvDirection;    // 방면(= routeName)
    private TextView tvBusPlate;     // 번호판
    private ImageView imgBusIcon;
    private LinearLayout colorHeaderBarContainer; // 승객 카드 헤더(정차시 색 변경)

    private TextView tvCurrentStop, tvNextStop;
    private final Handler arrivalHandler = new Handler();
    private final long ARRIVAL_POLL_MS = 10_000L;
    private final Runnable arrivalPollTask = new Runnable() {
        @Override public void run() {
            fetchArrivalNow();
            arrivalHandler.postDelayed(this, ARRIVAL_POLL_MS);
        }
    };

    // ==== 하단 제어 ====
    private Button btnDelay;
    private Button btnDriveEnd;
    private boolean isDelayActive = false;

    // ==== 운행 컨텍스트 ====
    private Long   operationId;
    private String vehicleId;
    private String routeId;
    private String routeName;
    private String plateNo;

    // ==== 위치/하트비트 ====
    private FusedLocationProviderClient fused;
    private LocationCallback locationCallback;
    private boolean sendingHeartbeat = false;
    private long lastSentMs = 0L;
    private static final long HEARTBEAT_MIN_INTERVAL_MS = 3000L; // 3초

    // ==== 정차 데모 ====
    private final Handler demoHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driving);

        api = ApiClient.get(getApplicationContext());
        fused = LocationServices.getFusedLocationProviderClient(this);

        // ==== 인텐트로 전달된 운행 정보 ====
        Intent i = getIntent();
        operationId = (i != null && i.hasExtra("operationId")) ? i.getLongExtra("operationId", -1) : -1;
        vehicleId   = (i != null) ? i.getStringExtra("vehicleId") : null;
        routeId     = (i != null) ? i.getStringExtra("routeId")   : null;
        routeName   = (i != null) ? i.getStringExtra("routeName") : null;

        // 번호판은 BusListActivity의 SharedPreferences에서 복원
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        if (operationId == -1) {
            long op = sp.getLong(K_OPERATION_ID, -1);
            if (op != -1) operationId = op;
        }
        plateNo = sp.getString(K_PLATE_NO, "-");

        // ==== 뷰 바인딩 ====
        tvBusNumber = findViewById(R.id.tvBusNumber);
        tvDirection = findViewById(R.id.tvDirection);
        tvBusPlate  = findViewById(R.id.tvBusPlate);
        imgBusIcon  = findViewById(R.id.imgBusIcon);
        colorHeaderBarContainer = findViewById(R.id.tvHeader);
        btnDelay    = findViewById(R.id.btnDelay);
        btnDriveEnd = findViewById(R.id.btnDriveEnd);

        tvCurrentStop = findViewById(R.id.tvCurrentStop);
        tvNextStop    = findViewById(R.id.tvNextStop);

        // ==== 상단 카드 표시 ====
        tvBusNumber.setText(routeId == null ? "-" : routeId);
        tvDirection.setText(routeName == null ? "-" : routeName);
        tvBusPlate.setText(plateNo == null ? "-" : plateNo);

        // 지연 버튼 초기 색(회색)
        btnDelay.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#CACACA")));
        btnDelay.setOnClickListener(v -> {
            if (isDelayActive) {
                btnDelay.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#CACACA")));
            } else {
                btnDelay.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF7800")));
            }
            isDelayActive = !isDelayActive;
            // TODO: 지연 이벤트 API 기록 필요 시 여기서 호출
        });

        btnDriveEnd.setOnClickListener(v -> showDriveEndPopup());

        // ---- 정차 신호 데모(5초 뒤 ON → 5초 뒤 OFF) ----
        demoHandler.postDelayed(this::simulateStopSignalOn, 5000);

        // ==== 위치 콜백 정의 ====
        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if (result == null || result.getLastLocation() == null) return;
                double lat = result.getLastLocation().getLatitude();
                double lon = result.getLastLocation().getLongitude();

                // 3초 레이트 리밋
                if (!sendingHeartbeat) return;
                long now = System.currentTimeMillis();
                if (now - lastSentMs < HEARTBEAT_MIN_INTERVAL_MS) return;
                lastSentMs = now;

                // 하트비트 전송 (Authorization 헤더는 OkHttp 인터셉터에서 부착된다고 가정)
                api.heartbeat(null, "DRIVER_APP",
                                new ApiService.HeartbeatRequest(lat, lon))
                        .enqueue(new Callback<Map<String, Object>>() {
                            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) { /* no-op */ }
                            @Override public void onFailure (Call<Map<String, Object>> call, Throwable t) { /* no-op */ }
                        });
            }
        };

        if (operationId == null || operationId <= 0) {
            // 잘못 들어온 경우(인텐트/로컬 복구 실패) → 메인으로
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
    }

    // ===== 라이프사이클에 맞춰 하트비트 시작/중지 =====
    @Override protected void onResume() {
        super.onResume();
        startLocationUpdates();
        arrivalHandler.removeCallbacksAndMessages(null);
        arrivalHandler.post(arrivalPollTask); // 즉시 1회 실행
    }

    @Override protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        arrivalHandler.removeCallbacksAndMessages(null);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        arrivalHandler.removeCallbacksAndMessages(null);
        demoHandler.removeCallbacksAndMessages(null);
    }

    private void fetchArrivalNow() {
        if (operationId == null || operationId <= 0) return;

        // Authorization 헤더는 OkHttp 인터셉터에서 자동 부착된다고 가정 → 여기서는 null 전달
        api.arrivalNow(null).enqueue(new Callback<ApiService.ArrivalNowResponse>() {
            @Override public void onResponse(
                    Call<ApiService.ArrivalNowResponse> call,
                    Response<ApiService.ArrivalNowResponse> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                ApiService.ArrivalNowResponse r = res.body();

                String cur = (r.currentStopName == null || r.currentStopName.isEmpty()) ? "-" : r.currentStopName;
                String next = (r.nextStopName == null || r.nextStopName.isEmpty()) ? "-" : r.nextStopName;

                if (r.etaSec != null) {
                    if (r.etaSec <= 0) {
                        next = next + " (곧 도착)";
                    } else {
                        int min = Math.max(1, (int)Math.round(r.etaSec / 60.0));
                        next = next + " (" + min + "분)";
                    }
                }

                tvCurrentStop.setText("이번 정류장 " + cur);
                tvNextStop.setText("다음 정류장 " + next);
            }
            @Override public void onFailure(Call<ApiService.ArrivalNowResponse> call, Throwable t) { /* no-op */ }
        });
    }

    private void startLocationUpdates() {
        if (operationId == null || operationId <= 0) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // 권한은 BusListActivity 흐름에서 처리된다고 가정. 여기서는 조용히 스킵.
            return;
        }

        // 고정밀 위치 요청
        LocationRequest req = new LocationRequest.Builder(2000L) // 권장 주기 2초
                .setMinUpdateIntervalMillis(1000L)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setWaitForAccurateLocation(true)
                .build();

        lastSentMs = 0L;
        sendingHeartbeat = true;
        fused.requestLocationUpdates(req, locationCallback, getMainLooper());
    }

    private void stopLocationUpdates() {
        sendingHeartbeat = false;
        if (fused != null && locationCallback != null) {
            fused.removeLocationUpdates(locationCallback);
        }
    }

    /**
     * 운행 종료 확인 팝업 → 서버로 종료 전송
     */
    private void showDriveEndPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_drive_end);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnCancel = dialog.findViewById(R.id.cancel_button);
        Button btnOk     = dialog.findViewById(R.id.bthOk);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnOk.setOnClickListener(v -> {
            btnOk.setEnabled(false);
            callEndOperation(dialog);
        });

        dialog.show();
    }

    /**
     * /api/driver/operations/end 호출
     */
    private void callEndOperation(Dialog dialogToDismiss) {
        api.endOperation(null, "DRIVER_APP", new ApiService.EndOperationRequest(""))
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                        if (!res.isSuccessful()) {
                            Toast.makeText(DrivingActivity.this, "운행 종료 실패: " + res.code(), Toast.LENGTH_SHORT).show();
                            if (dialogToDismiss != null) dialogToDismiss.dismiss();
                            return;
                        }
                        // ✅ 운행 종료 성공 → 로컬 저장된 operationId 삭제
                        SharedPreferences sp = getSharedPreferences("driver_prefs", MODE_PRIVATE);
                        sp.edit().remove("sel_operation_id").apply();

                        Toast.makeText(DrivingActivity.this, "운행이 종료되었습니다.", Toast.LENGTH_SHORT).show();

                        // 스택 정리 후 메인으로
                        Intent intent = new Intent(DrivingActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(DrivingActivity.this, "종료 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        if (dialogToDismiss != null) dialogToDismiss.dismiss();
                    }
                });
    }

    // ===== 정차 신호 데모 (실연동 시 이벤트 수신부에서 호출) =====
    private void simulateStopSignalOn() {
        colorHeaderBarContainer.setBackgroundColor(Color.parseColor("#FFEBEE")); // 연한 빨강
        demoHandler.postDelayed(this::simulateStopSignalOff, 5000);
    }

    private void simulateStopSignalOff() {
        colorHeaderBarContainer.setBackgroundColor(Color.TRANSPARENT);
    }
}
