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

public class DrivingActivity extends AppCompatActivity {

    private static final String PREF = "driver_prefs";
    private static final String K_OPERATION_ID = "sel_operation_id";
    private static final String K_PLATE_NO     = "sel_plate_no";

    private ApiService api;

    // 상단 2열
    private TextView tvRouteName, tvPlateNo, tvRouteId, tvVehicleId;

    // 정류장 안내(세로 배치)
    private TextView tvCurrentStop, tvNextStop;

    private ImageView imgBusIcon;
    private LinearLayout colorHeaderBarContainer; // 승객 카드 헤더 틴트

    private Button btnDelay, btnDriveEnd;
    private boolean isDelayActive = false;

    private Long   operationId;
    private String vehicleId;
    private String routeId;
    private String routeName;
    private String plateNo;

    private FusedLocationProviderClient fused;
    private LocationCallback locationCallback;
    private boolean sendingHeartbeat = false;
    private long lastSentMs = 0L;
    private static final long HEARTBEAT_MIN_INTERVAL_MS = 3000L;

    private final Handler arrivalHandler = new Handler();
    private final long ARRIVAL_POLL_MS = 10_000L;
    private final Runnable arrivalPollTask = new Runnable() {
        @Override public void run() {
            fetchArrivalNow();
            arrivalHandler.postDelayed(this, ARRIVAL_POLL_MS);
        }
    };

    private final Handler demoHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driving);

        api = ApiClient.get(getApplicationContext());
        fused = LocationServices.getFusedLocationProviderClient(this);

        Intent i = getIntent();
        operationId = (i != null && i.hasExtra("operationId")) ? i.getLongExtra("operationId", -1) : -1;
        vehicleId   = (i != null) ? i.getStringExtra("vehicleId") : null;
        routeId     = (i != null) ? i.getStringExtra("routeId")   : null;
        routeName   = (i != null) ? i.getStringExtra("routeName") : null;

        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        if (operationId == -1) {
            long op = sp.getLong(K_OPERATION_ID, -1);
            if (op != -1) operationId = op;
        }
        plateNo = sp.getString(K_PLATE_NO, "-");

        imgBusIcon  = findViewById(R.id.imgBusIcon);
        colorHeaderBarContainer = findViewById(R.id.passengerHeaderTint);

        tvRouteName = findViewById(R.id.tvRouteName);
        tvPlateNo   = findViewById(R.id.tvPlateNo);
        tvRouteId   = findViewById(R.id.tvRouteId);
        tvVehicleId = findViewById(R.id.tvVehicleId);

        tvCurrentStop = findViewById(R.id.tvCurrentStop);
        tvNextStop    = findViewById(R.id.tvNextStop);

        btnDelay    = findViewById(R.id.btnDelay);
        btnDriveEnd = findViewById(R.id.btnDriveEnd);

        // 상단 값 세팅
        tvRouteName.setText(routeName == null ? "-" : routeName);
        tvPlateNo.setText(plateNo == null ? "-" : plateNo);
        tvRouteId.setText(routeId == null ? "-" : routeId);
        tvVehicleId.setText(vehicleId == null ? "-" : vehicleId);

        // 지연 버튼 토글
        btnDelay.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#BFC3CC")));
        btnDelay.setOnClickListener(v -> {
            if (isDelayActive) {
                btnDelay.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#BFC3CC")));
            } else {
                btnDelay.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF7800")));
            }
            isDelayActive = !isDelayActive;
        });

        btnDriveEnd.setOnClickListener(v -> showDriveEndPopup());

        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if (result == null || result.getLastLocation() == null) return;
                double lat = result.getLastLocation().getLatitude();
                double lon = result.getLastLocation().getLongitude();

                if (!sendingHeartbeat) return;
                long now = System.currentTimeMillis();
                if (now - lastSentMs < HEARTBEAT_MIN_INTERVAL_MS) return;
                lastSentMs = now;

                api.heartbeat(null, "DRIVER_APP",
                                new ApiService.HeartbeatRequest(lat, lon))
                        .enqueue(new Callback<Map<String, Object>>() {
                            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) { }
                            @Override public void onFailure (Call<Map<String, Object>> call, Throwable t) { }
                        });
            }
        };

        if (operationId == null || operationId <= 0) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        // 데모: 정차 틴트
        demoHandler.postDelayed(this::simulateStopSignalOn, 5000);
    }

    @Override protected void onResume() {
        super.onResume();
        startLocationUpdates();
        arrivalHandler.removeCallbacksAndMessages(null);
        arrivalHandler.post(arrivalPollTask);
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

        api.arrivalNow(null).enqueue(new Callback<ApiService.ArrivalNowResponse>() {
            @Override public void onResponse(Call<ApiService.ArrivalNowResponse> call,
                                             Response<ApiService.ArrivalNowResponse> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                ApiService.ArrivalNowResponse r = res.body();

                String cur  = (r.currentStopName == null || r.currentStopName.isEmpty()) ? "-" : r.currentStopName;
                String next = (r.nextStopName == null || r.nextStopName.isEmpty()) ? "-" : r.nextStopName;

                if (r.etaSec != null) {
                    if (r.etaSec <= 0) next = next + " (곧 도착)";
                    else {
                        int min = Math.max(1, (int)Math.round(r.etaSec / 60.0));
                        next = next + " (" + min + "분)";
                    }
                }

                // 중앙정렬 카드에 값만 채움
                tvCurrentStop.setText(cur);
                tvNextStop.setText(next);
            }
            @Override public void onFailure(Call<ApiService.ArrivalNowResponse> call, Throwable t) { }
        });
    }

    private void startLocationUpdates() {
        if (operationId == null || operationId <= 0) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationRequest req = new LocationRequest.Builder(2000L)
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

    private void showDriveEndPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_drive_end);

        Window w = dialog.getWindow();
        if (w != null) {
            // 배경을 투명으로 하고, 크기는 내용물에 맞추고, 중앙 정렬
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(android.view.Gravity.CENTER);
            // 필요 시 살짝 애니메이션(옵션)
            // w.setWindowAnimations(android.R.style.Animation_Dialog);
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
                        SharedPreferences sp = getSharedPreferences("driver_prefs", MODE_PRIVATE);
                        sp.edit().remove("sel_operation_id").apply();

                        Toast.makeText(DrivingActivity.this, "운행이 종료되었습니다.", Toast.LENGTH_SHORT).show();

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

    // 데모 틴트
    private void simulateStopSignalOn() {
        if (colorHeaderBarContainer != null) {
            colorHeaderBarContainer.setBackgroundColor(Color.parseColor("#FFEBEE"));
            demoHandler.postDelayed(this::simulateStopSignalOff, 5000);
        }
    }
    private void simulateStopSignalOff() {
        if (colorHeaderBarContainer != null) {
            colorHeaderBarContainer.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
