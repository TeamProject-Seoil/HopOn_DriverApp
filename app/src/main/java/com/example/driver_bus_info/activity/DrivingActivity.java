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
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
// BuildConfig는 앱 패키지의 것을 사용


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DrivingActivity extends AppCompatActivity {

    // ===== Local prefs keys =====
    private static final String PREF           = "driver_prefs";
    private static final String K_OPERATION_ID = "sel_operation_id";
    private static final String K_PLATE_NO     = "sel_plate_no";
    private static final String K_STARTED_AT   = "operation_started_at";

    private static final String TAG = "DrivingActivity";

    private ApiService api;

    // 상단
    private TextView tvRouteName, tvPlateNo, tvRouteType;
    private ImageView imgBusIcon;
    private LinearLayout colorHeaderBarContainer; // 승객 카드 헤더 틴트(데모)

    // 정류장 안내
    private TextView tvCurrentStop, tvNextStop;

    // 현재 정류장 표준화된 이름/ID
    private String currentStopName = null;   // 원문
    private String currentStopNorm = null;   // normalize 결과
    private String currentStopId   = null;   // 서버가 주면 사용

    // 승객 현황(타일)
    private TextView tvBoardingNum, tvDropoffNum; // 이번 역 탑승/하차 예정
    private TextView tvTotalReservedBadge; // (옵션) 총 예약 배지

    // 총 운행시간
    private TextView tvTotalDriveTime;
    private final Handler timerHandler = new Handler();
    private long operationStartedAtMs = 0L;
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            long base = (operationStartedAtMs == 0L) ? System.currentTimeMillis() : operationStartedAtMs;
            long elapsed = Math.max(0L, System.currentTimeMillis() - base);
            if (tvTotalDriveTime != null) tvTotalDriveTime.setText(formatHms(elapsed));
            timerHandler.postDelayed(this, 1000);
        }
    };

    private Button btnDelay, btnDriveEnd;
    private boolean isDelayActive = false;

    // 인텐트/상태
    private Long   operationId;
    private String vehicleId;
    private String routeId;
    private String routeName;
    private String plateNo;

    // 위치 & 하트비트
    private FusedLocationProviderClient fused;
    private LocationCallback locationCallback;
    private boolean sendingHeartbeat = false;
    private long lastSentMs = 0L;
    private static final long HEARTBEAT_MIN_INTERVAL_MS = 3000L;

    // 이번/다음 정류장 + 승객 폴링
    private final Handler arrivalHandler = new Handler();
    private static final long ARRIVAL_POLL_MS = 10_000L;
    private final Runnable arrivalPollTask = new Runnable() {
        @Override public void run() {
            fetchArrivalNowAndThenFetchPassengers();
            arrivalHandler.postDelayed(this, ARRIVAL_POLL_MS);
        }
    };

    // ====== 체인: 도착정보 반영 후 승객 집계 ======
    private void fetchArrivalNowAndThenFetchPassengers() {
        if (operationId == null || operationId <= 0) return;

        api.arrivalNow(null, "DRIVER_APP").enqueue(new Callback<ApiService.ArrivalNowResponse>() {
            @Override public void onResponse(Call<ApiService.ArrivalNowResponse> call,
                                             Response<ApiService.ArrivalNowResponse> res) {
                if (res.isSuccessful() && res.body() != null) {
                    ApiService.ArrivalNowResponse r = res.body();

                    // 1) 노선유형 UI 반영
                    String  label = !nz(r.routeTypeLabel).isEmpty() ? r.routeTypeLabel : codeToLabel(r.routeTypeCode);
                    Integer code  = r.routeTypeCode != null ? r.routeTypeCode : labelToCode(label);
                    if (label != null) tvRouteType.setText(label);
                    if (code  != null) applyRouteTypeColor(code);

                    // 2) 정류장 텍스트 + 현재 정류장 표준화
                    String cur  = nz(r.currentStopName).isEmpty() ? "-" : r.currentStopName;
                    String next = nz(r.nextStopName).isEmpty()    ? "-" : r.nextStopName;

                    if (r.etaSec != null) {
                        if (r.etaSec <= 0) next = next + "  ·  곧 도착";
                        else next = next + "  ·  약 " + Math.max(1, (int)Math.round(r.etaSec/60.0)) + "분";
                    }
                    tvCurrentStop.setText(cur);
                    tvNextStop.setText(next);

                    currentStopName = "-".equals(cur) ? "" : cur;
                    currentStopNorm = normalizeStop(currentStopName);
                    currentStopId   = nz(r.currentStopId); // 서버가 주면 세팅
                }
                // 3) arrival 반영 직후 승객 집계
                fetchPassengersNow();
            }
            @Override public void onFailure(Call<ApiService.ArrivalNowResponse> call, Throwable t) {
                fetchPassengersNow(); // 실패해도 집계는 시도
            }
        });
    }

    // 데모용
    private final Handler demoHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driving);

        api = ApiClient.get(getApplicationContext());
        fused = LocationServices.getFusedLocationProviderClient(this);

        // 인텐트/로컬 상태 복구
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

        // 뷰 바인딩
        imgBusIcon  = findViewById(R.id.imgBusIcon);
        colorHeaderBarContainer = findViewById(R.id.passengerHeaderTint);

        tvRouteName = findViewById(R.id.tvRouteName);
        tvPlateNo   = findViewById(R.id.tvPlateNo);
        tvRouteType = findViewById(R.id.tvRouteType);

        tvCurrentStop = findViewById(R.id.tvCurrentStop);
        tvNextStop    = findViewById(R.id.tvNextStop);

        tvTotalDriveTime = findViewById(R.id.tvTotalDriveTime);

        // 승객 카드 내 카운터
        tvBoardingNum = findViewById(R.id.tvBoardingNum);
        tvDropoffNum  = findViewById(R.id.tvDropoffNum);
        tvTotalReservedBadge = findViewById(R.id.tvTotalReserved); // 없으면 null

        // 운행시간 기준시각 복구(처음이면 지금 시각 저장)
        operationStartedAtMs = sp.getLong(K_STARTED_AT, 0L);
        if (operationStartedAtMs == 0L) {
            operationStartedAtMs = System.currentTimeMillis();
            sp.edit().putLong(K_STARTED_AT, operationStartedAtMs).apply();
        }

        btnDelay    = findViewById(R.id.btnDelay);
        btnDriveEnd = findViewById(R.id.btnDriveEnd);

        // 상단 기본 표시
        tvRouteName.setText(routeName == null ? "-" : routeName);
        tvPlateNo.setText(plateNo == null ? "-" : plateNo);
        tvRouteType.setText("-"); // 첫 렌더링 기본값

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

        // 운행 종료
        btnDriveEnd.setOnClickListener(v -> showDriveEndPopup());

        // 하트비트 콜백
        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if (result == null || result.getLastLocation() == null) return;
                if (!sendingHeartbeat) return;

                long now = System.currentTimeMillis();
                if (now - lastSentMs < HEARTBEAT_MIN_INTERVAL_MS) return;
                lastSentMs = now;

                double lat = result.getLastLocation().getLatitude();
                double lon = result.getLastLocation().getLongitude();

                api.heartbeat(null, "DRIVER_APP",
                                new ApiService.HeartbeatRequest(lat, lon))
                        .enqueue(new Callback<Map<String, Object>>() {
                            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) { /* noop */ }
                            @Override public void onFailure (Call<Map<String, Object>> call, Throwable t) { /* noop */ }
                        });
            }
        };

        // 방어: operationId 없으면 메인으로 복귀
        if (operationId == null || operationId <= 0) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        // 데모: 5초 후 상단 바 틴트 on/off
        demoHandler.postDelayed(this::simulateStopSignalOn, 5000);
    }

    @Override protected void onResume() {
        super.onResume();
        startLocationUpdates();

        // 이번/다음 정류장 & 승객 폴링 시작
        arrivalHandler.removeCallbacksAndMessages(null);
        arrivalHandler.post(arrivalPollTask);

        // 타이머 시작
        timerHandler.removeCallbacksAndMessages(null);
        timerHandler.post(timerTick);

        // 즉시 1회 정보 갱신
        fetchArrivalNowAndThenFetchPassengers();
    }

    @Override protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        arrivalHandler.removeCallbacksAndMessages(null);
        timerHandler.removeCallbacksAndMessages(null);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        arrivalHandler.removeCallbacksAndMessages(null);
        demoHandler.removeCallbacksAndMessages(null);
        timerHandler.removeCallbacksAndMessages(null);
    }

    // ===== 승객 현황(리스트 없음, 카운트만) =====
    private void fetchPassengersNow() {
        if (operationId == null || operationId <= 0) {
            updatePassengerCounters(0, 0, 0);
            return;
        }

        api.getDriverPassengers(null, "DRIVER_APP")
                .enqueue(new Callback<ApiService.DriverPassengerListResponse>() {
                    @Override public void onResponse(
                            Call<ApiService.DriverPassengerListResponse> call,
                            Response<ApiService.DriverPassengerListResponse> res) {
                        if (!res.isSuccessful() || res.body() == null) {
                            updatePassengerCounters(0, 0, 0);
                            return;
                        }
                        List<ApiService.DriverPassengerDto> items =
                                res.body().items != null ? res.body().items : new ArrayList<>();

                        int boardingHere  = 0;   // 이번 역 승차 예정
                        int alightHere    = 0;   // 이번 역 하차 예정
                        int totalReserved = 0;   // 총 예약(취소/노쇼 제외)

                        final String curId  = nz(currentStopId).trim();
                        final String curNm  = nz(currentStopName).trim();
                        final String curNor = nz(currentStopNorm);

                        for (ApiService.DriverPassengerDto d : items) {
                            String st = nz(d.status).toUpperCase();

                            boolean reservedLike = st.contains("CONFIRM") || st.contains("RESERV");
                            boolean boardedLike  = st.contains("BOARD");

                            if (reservedLike || boardedLike) totalReserved++;

                            boolean matchBoardHere = false;
                            boolean matchAlightHere = false;

                            if (!curId.isEmpty()) {
                                if (curId.equals(nz(d.boardingStopId).trim()))  matchBoardHere  = true;
                                if (curId.equals(nz(d.alightingStopId).trim())) matchAlightHere = true;
                            } else {
                                String bNor = normalizeStop(d.boardingStopName);
                                String aNor = normalizeStop(d.alightingStopName);
                                if (!curNor.isEmpty() && curNor.equals(bNor)) matchBoardHere = true;
                                if (!curNor.isEmpty() && curNor.equals(aNor)) matchAlightHere = true;
                            }

                            if (reservedLike && matchBoardHere)  boardingHere++;
                            if (boardedLike  && matchAlightHere) alightHere++;
                        }

                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "passengers: items=" + items.size()
                                    + ", curNm=" + curNm + ", curId=" + curId
                                    + " -> boardHere=" + boardingHere + ", alightHere=" + alightHere
                                    + ", total=" + totalReserved);
                        }

                        updatePassengerCounters(boardingHere, alightHere, totalReserved);
                    }

                    @Override public void onFailure(
                            Call<ApiService.DriverPassengerListResponse> call, Throwable t) {
                        updatePassengerCounters(0, 0, 0);
                    }
                });
    }

    private void updatePassengerCounters(int boardingHere, int alightHere, int totalReserved) {
        if (tvBoardingNum != null) tvBoardingNum.setText(boardingHere + "명");
        if (tvDropoffNum  != null) tvDropoffNum.setText(alightHere   + "명");
        if (tvTotalReservedBadge != null) {
            tvTotalReservedBadge.setText("총 예약 " + totalReserved + "명");
        }
    }

    // ===== 위치 업데이트(하트비트) =====
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

    // ===== 운행 종료 =====
    private void showDriveEndPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_drive_end);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(android.view.Gravity.CENTER);
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
                        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
                        sp.edit()
                                .remove(K_OPERATION_ID)
                                .remove(K_STARTED_AT)
                                .apply();

                        timerHandler.removeCallbacksAndMessages(null);

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

    // ===== 스타일/유틸 =====
    private void applyRouteTypeColor(Integer code){
        int color = routeTypeColor(code);
        if (tvRouteName != null) tvRouteName.setTextColor(color);
        if (tvRouteType != null) {
            tvRouteType.setTextColor(color);
            tvRouteType.setBackgroundTintList(ColorStateList.valueOf(adjustAlpha(color, 0.15f)));
        }
        setRoundedIconBg(imgBusIcon, color, 12f);
    }

    private int routeTypeColor(Integer code) {
        if (code == null) return Color.parseColor("#6B7280"); // 기타
        switch (code) {
            case 1: return Color.parseColor("#0288D1"); // 공항
            case 2: return Color.parseColor("#6A1B9A"); // 마을
            case 3: return Color.parseColor("#1976D2"); // 간선
            case 4: return Color.parseColor("#2E7D32"); // 지선
            case 5: return Color.parseColor("#F9A825"); // 순환
            case 6: return Color.parseColor("#C62828"); // 광역
            case 7: return Color.parseColor("#1565C0"); // 인천
            case 8: return Color.parseColor("#00695C"); // 경기
            case 9: return Color.parseColor("#374151"); // 폐지
            case 0:
            default: return Color.parseColor("#6B7280"); // 공용/기타
        }
    }

    private String codeToLabel(Integer code) {
        if (code == null) return null;
        switch (code) {
            case 1: return "공항";
            case 2: return "마을";
            case 3: return "간선";
            case 4: return "지선";
            case 5: return "순환";
            case 6: return "광역";
            case 7: return "인천";
            case 8: return "경기";
            case 9: return "폐지";
            case 0: return "공용";
            default: return "기타";
        }
    }
    private Integer labelToCode(String label){
        if (label == null) return null;
        switch (label.trim()){
            case "공항": return 1;
            case "마을": return 2;
            case "간선": return 3;
            case "지선": return 4;
            case "순환": return 5;
            case "광역": return 6;
            case "인천": return 7;
            case "경기": return 8;
            case "폐지": return 9;
            case "공용": return 0;
            default: return null;
        }
    }
    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
    private void setRoundedIconBg(ImageView iv, int color, float radiusDp){
        if (iv == null) return;
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        gd.setColor(color);
        gd.setStroke(1, color);
        iv.setBackground(gd);
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(Color.WHITE));
    }

    // 경과시간 포맷
    private String formatHms(long ms){
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
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

    private static String nz(String s) { return s == null ? "" : s; }

    /** 정류장 이름 정규화 */
    private static String normalizeStop(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase();

        s = s.replaceAll("\\(.*?\\)", "");
        s = s.replaceAll("\\[.*?\\]", "");
        s = s.replaceAll("\\{.*?\\}", "");

        s = s.replace("·", "");
        s = s.replace("ㆍ", "");
        s = s.replace(".", "");
        s = s.replace("-", "");
        s = s.replace("_", "");

        s = s.replaceAll("\\s+", "");

        s = s.replace("정류장", "");
        s = s.replace("역", "");

        return s.trim();
    }
}
