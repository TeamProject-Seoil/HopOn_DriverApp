package com.example.driver_bus_info.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.adapter.RegistrationAdapter;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 등록 이력 리스트 + 선택 카드 + 운행 시작 */
public class BusListActivity extends AppCompatActivity implements ActivityResultCaller {

    private ImageButton btnBack;
    private Button btnNewBus, bthDriveStart;

    private TextView tvSelectedBusNumber, tvSelectedRoute, tvSelectedStatus, tvSelectedPlate;
    private TextView tvSelectedRouteType;
    private ImageView ivSelectedIcon;
    private ImageButton btnSelectedRemove;

    private TextView tvRegEmpty;
    private RecyclerView rvRegistrations;

    private ApiService api;

    // pref keys
    private static final String PREF = "driver_prefs";
    private static final String K_VEHICLE_ID="sel_vehicle_id";
    private static final String K_PLATE_NO ="sel_plate_no";
    private static final String K_ROUTE_ID ="sel_route_id";
    private static final String K_ROUTE_NAME="sel_route_name";
    private static final String K_OPERATION_ID="sel_operation_id";

    private @Nullable String selVehicleId, selPlateNo, selRouteId, selRouteName;

    // 위치
    private FusedLocationProviderClient fused;
    private ActivityResultLauncher<String> permLauncher;
    private Dialog searchingDialog;

    // 위치 최신화 컨트롤
    private CancellationTokenSource currentCts;
    private LocationCallback singleCallback;
    private final Handler handler = new Handler();

    // 자동 매칭 재시도 컨트롤
    private volatile boolean keepMatching = false;
    private int matchAttempt = 0;
    private Runnable pendingRetry = null;
    private Double lastLat = null, lastLon = null;
    private static final int MAX_ATTEMPTS = 60; // 안전상한

    // 리스트
    private RegistrationAdapter regAdapter;
    private List<ApiService.DriverVehicleRegistrationDto> lastRegs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_list);

        api = ApiClient.get(getApplicationContext());
        fused = LocationServices.getFusedLocationProviderClient(this);

        permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) fetchLocationAndStartOperation();
                    else { dismissSearchingPopup(); Toast.makeText(this, "정밀 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show(); }
                }
        );

        // 뷰
        btnBack = findViewById(R.id.btn_back);
        btnNewBus = findViewById(R.id.btnNewBus);
        bthDriveStart = findViewById(R.id.bthDriveStart);
        tvSelectedBusNumber = findViewById(R.id.tvSelectedBusNumber);
        tvSelectedRoute = findViewById(R.id.tvSelectedRoute);
        tvSelectedPlate = findViewById(R.id.tvSelectedPlate);
        tvSelectedStatus = findViewById(R.id.tvSelectedStatus);
        tvSelectedRouteType = findViewById(R.id.tvSelectedRouteType);
        ivSelectedIcon = findViewById(R.id.ivSelectedIcon);
        btnSelectedRemove = findViewById(R.id.btnSelectedRemove);
        tvRegEmpty = findViewById(R.id.tvRegEmpty);
        rvRegistrations = findViewById(R.id.rvRegistrations);

        // 뒤로
        btnBack.setOnClickListener(v -> { startActivity(new Intent(this, MainActivity.class)); finish(); });

        // 선택 카드 X버튼: 현재 선택/등록 해제 (서버 삭제 없이 로컬만)
        if (btnSelectedRemove != null) {
            btnSelectedRemove.setOnClickListener(v -> {
                if (selVehicleId == null) {
                    Toast.makeText(this, "선택된 차량이 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                clearSelectedVehicle();
                updateSelectedCard();
                regAdapter.setSelectedVehicleId(null);
                Toast.makeText(BusListActivity.this, "선택을 해제했습니다.", Toast.LENGTH_SHORT).show();
            });
        }

        // 리스트 어댑터
        regAdapter = new RegistrationAdapter(
                new ArrayList<>(),
                item -> {
                    ApiService.AssignVehicleRequest body =
                            new ApiService.AssignVehicleRequest(item.vehicleId, null, "DRIVER_APP");

                    api.assignVehicle(null, "DRIVER_APP", body)
                            .enqueue(new Callback<ApiService.AssignVehicleResponse>() {
                                @Override public void onResponse(Call<ApiService.AssignVehicleResponse> call,
                                                                 Response<ApiService.AssignVehicleResponse> res) {
                                    if (!res.isSuccessful()) {
                                        if (res.code()==409) {
                                            Toast.makeText(BusListActivity.this,
                                                    "운행 중에는 변경 불가. 운행 종료 후 다시 선택하세요.", Toast.LENGTH_LONG).show();
                                        } else if (res.code()==404) {
                                            Toast.makeText(BusListActivity.this,
                                                    "차량 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(BusListActivity.this,
                                                    "배정 실패: " + res.code(), Toast.LENGTH_SHORT).show();
                                        }
                                        return;
                                    }

                                    ApiService.AssignVehicleResponse r = res.body();
                                    if (r == null) { Toast.makeText(BusListActivity.this,"배정 실패(빈 응답).",Toast.LENGTH_SHORT).show(); return; }

                                    saveSelectedVehicle(r.vehicleId, r.plateNo, r.routeId, r.routeName);
                                    regAdapter.setSelectedVehicleId(r.vehicleId);
                                    updateSelectedCard(); // 기본값 그리기

                                    // 선택한 아이템의 메타 즉시 카드에 반영
                                    String rtLabel = (item.routeTypeLabel != null && !item.routeTypeLabel.isBlank())
                                            ? item.routeTypeLabel : codeToLabel(item.routeTypeCode);
                                    Integer rtCode  = (item.routeTypeCode != null) ? item.routeTypeCode : labelToCode(rtLabel);
                                    if (rtLabel != null) tvSelectedRouteType.setText(rtLabel);
                                    applySelectedMetaColor(rtCode);

                                    // 보정 루틴
                                    applyMetaFromRegs(r.vehicleId);
                                    loadArrivalMeta();
                                    setDriveStartEnabled(true);

                                    Toast.makeText(BusListActivity.this,
                                            "선택됨: " + (r.plateNo==null ? r.vehicleId : r.plateNo),
                                            Toast.LENGTH_SHORT).show();
                                }

                                @Override public void onFailure(Call<ApiService.AssignVehicleResponse> call, Throwable t) {
                                    Toast.makeText(BusListActivity.this, "배정 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                },
                item -> api.deleteDriverRegistration(null, "DRIVER_APP", item.vehicleId)
                        .enqueue(new Callback<Void>() {
                            @Override public void onResponse(Call<Void> call, Response<Void> response) { loadRegistrations(); }
                            @Override public void onFailure(Call<Void> call, Throwable t) {
                                Toast.makeText(BusListActivity.this, "삭제 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        })
        );

        rvRegistrations.setLayoutManager(new LinearLayoutManager(this));
        rvRegistrations.setAdapter(regAdapter);

        // 버튼
        btnNewBus.setOnClickListener(v -> showBusRegisterPopup());
        bthDriveStart.setOnClickListener(v -> showBusConfirmPopup());

        // 초기 상태
        loadSelectedVehicle();
        regAdapter.setSelectedVehicleId(selVehicleId);
        updateSelectedCard();
        loadRegistrations();
        loadArrivalMeta();
    }

    private void setDriveStartEnabled(boolean enabled) {
        bthDriveStart.setEnabled(enabled);
        bthDriveStart.setAlpha(enabled ? 1f : 0.5f);
    }

    // ===== 등록 이력 불러오기 =====
    private void loadRegistrations() {
        api.getDriverRegistrations(null, "DRIVER_APP")
                .enqueue(new Callback<List<ApiService.DriverVehicleRegistrationDto>>() {
                    @Override public void onResponse(Call<List<ApiService.DriverVehicleRegistrationDto>> call,
                                                     Response<List<ApiService.DriverVehicleRegistrationDto>> res) {
                        List<ApiService.DriverVehicleRegistrationDto> list =
                                res.isSuccessful() && res.body()!=null ? res.body() : new ArrayList<>();
                        lastRegs = list; // 캐시
                        regAdapter.submit(list);
                        regAdapter.setSelectedVehicleId(selVehicleId);
                        tvRegEmpty.setVisibility(list.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                        applyMetaFromRegs(selVehicleId);
                        if (selVehicleId != null) {
                            Integer code = findRouteTypeCodeInCache(selVehicleId);
                            if (code != null) applySelectedMetaColor(code);
                        }
                    }
                    @Override public void onFailure(Call<List<ApiService.DriverVehicleRegistrationDto>> call, Throwable t) {
                        tvRegEmpty.setVisibility(android.view.View.VISIBLE);
                        Toast.makeText(BusListActivity.this, "이력 불러오기 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ===== SharedPreferences =====
    private void saveSelectedVehicle(String vehicleId, String plateNo, String routeId, String routeName) {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        sp.edit()
                .putString(K_VEHICLE_ID, vehicleId)
                .putString(K_PLATE_NO,   plateNo)
                .putString(K_ROUTE_ID,   routeId)
                .putString(K_ROUTE_NAME, routeName)
                .apply();
        selVehicleId = vehicleId; selPlateNo = plateNo; selRouteId = routeId; selRouteName = routeName;
    }

    private void saveOperationId(@Nullable Long opId) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putLong(K_OPERATION_ID, opId == null ? -1 : opId).apply();
    }

    private void loadSelectedVehicle() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        selVehicleId = sp.getString(K_VEHICLE_ID, null);
        selPlateNo   = sp.getString(K_PLATE_NO, null);
        selRouteId   = sp.getString(K_ROUTE_ID, null);
        selRouteName = sp.getString(K_ROUTE_NAME, null);
    }

    private void clearSelectedVehicle() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        sp.edit()
                .remove(K_VEHICLE_ID)
                .remove(K_PLATE_NO)
                .remove(K_ROUTE_ID)
                .remove(K_ROUTE_NAME)
                .apply();
        selVehicleId = null; selPlateNo = null; selRouteId = null; selRouteName = null;
        regAdapter.setSelectedVehicleId(null);
        setDriveStartEnabled(false);
    }

    private void updateSelectedCard() {
        boolean has = !TextUtils.isEmpty(selVehicleId) && !TextUtils.isEmpty(selPlateNo);

        tvSelectedRoute.setText(selRouteName == null ? "-" : selRouteName);
        tvSelectedPlate.setText(selPlateNo == null ? "-" : selPlateNo);
        tvSelectedBusNumber.setText(selRouteName == null ? "-" : selRouteName);

        if (!has) {
            tvSelectedRouteType.setText("-");
            tvSelectedRoute.setTextColor(parseColor("#111111"));
            tvSelectedRouteType.setTextColor(parseColor("#6B7280"));
            tintCircleBackground(ivSelectedIcon, parseColor("#D1D5DB"));
            tvSelectedStatus.setText("차량 상태: -");
            setDriveStartEnabled(false);
            return;
        }

        Integer codeFromCache  = findRouteTypeCodeInCache(selVehicleId);
        String  labelFromCache = findRouteTypeLabelInCache(selVehicleId);

        if (labelFromCache != null) tvSelectedRouteType.setText(labelFromCache);

        if (codeFromCache != null) {
            applySelectedMetaColor(codeFromCache);
        } else if (labelFromCache != null) {
            Integer fallback = labelToCode(labelFromCache);
            if (fallback != null) applySelectedMetaColor(fallback);
        }

        tvSelectedStatus.setText("차량 상태: 선택됨(대기)");
        setDriveStartEnabled(true);
    }

    /** 등록 이력 캐시에서 선택 차량 메타를 카드에 반영 + 색상/아이콘 틴트 적용 */
    private void applyMetaFromRegs(@Nullable String vehicleId) {
        if (vehicleId == null || lastRegs == null) return;
        for (ApiService.DriverVehicleRegistrationDto it : lastRegs) {
            if (vehicleId.equals(it.vehicleId)) {
                String label = (it.routeTypeLabel != null && !it.routeTypeLabel.isBlank())
                        ? it.routeTypeLabel
                        : codeToLabel(it.routeTypeCode);
                if (label != null) tvSelectedRouteType.setText(label);

                Integer code = (it.routeTypeCode != null) ? it.routeTypeCode : labelToCode(label);
                applySelectedMetaColor(code);
                break;
            }
        }
    }

    /** 선택 카드용 메타(노선유형) 실시간 보정 + 색상/아이콘 틴트 */
    private void loadArrivalMeta() {
        final String expectVehicleId = selVehicleId;

        api.arrivalNow(null,"DRIVER_APP").enqueue(new Callback<ApiService.ArrivalNowResponse>() {
            @Override public void onResponse(Call<ApiService.ArrivalNowResponse> call,
                                             Response<ApiService.ArrivalNowResponse> res) {
                if (!res.isSuccessful() || res.body()==null) return;
                if (!TextUtils.equals(expectVehicleId, selVehicleId)) return;

                ApiService.ArrivalNowResponse r = res.body();

                String  label = (r.routeTypeLabel != null && !r.routeTypeLabel.isBlank())
                        ? r.routeTypeLabel : codeToLabel(r.routeTypeCode);
                Integer code  = (r.routeTypeCode != null) ? r.routeTypeCode : labelToCode(label);

                if (code == null && (label == null || label.isBlank())) return;

                if (label != null) tvSelectedRouteType.setText(label);
                if (code  != null) applySelectedMetaColor(code);
            }
            @Override public void onFailure(Call<ApiService.ArrivalNowResponse> call, Throwable t) { /* noop */ }
        });
    }

    /** 선택 카드 색상/아이콘 배경 틴트를 일괄 적용 */
    private void applySelectedMetaColor(@Nullable Integer routeTypeCode) {
        int color = routeTypeColor(routeTypeCode);
        tvSelectedRoute.setTextColor(color);
        tvSelectedRouteType.setTextColor(color);
        tintCircleBackground(ivSelectedIcon, color);
    }

    /** bg_bus_circle(Shape) 에 안정적으로 틴트 적용(선택 카드) */
    private void tintCircleBackground(@Nullable ImageView iv, int color) {
        if (iv == null) return;
        ViewCompat.setBackgroundTintList(iv, android.content.res.ColorStateList.valueOf(color));
        androidx.core.widget.ImageViewCompat.setImageTintList(
                iv, android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        );
    }

    /** 캐시에서 현재 선택 차량의 유형코드 찾기 */
    private @Nullable Integer findRouteTypeCodeInCache(@Nullable String vehicleId) {
        if (vehicleId == null || lastRegs == null) return null;
        for (ApiService.DriverVehicleRegistrationDto it : lastRegs) {
            if (vehicleId.equals(it.vehicleId)) return it.routeTypeCode;
        }
        return null;
    }

    /** 캐시에서 현재 선택 차량의 유형라벨 찾기 */
    private @Nullable String findRouteTypeLabelInCache(@Nullable String vehicleId) {
        if (vehicleId == null || lastRegs == null) return null;
        for (ApiService.DriverVehicleRegistrationDto it : lastRegs) {
            if (vehicleId.equals(it.vehicleId)) {
                if (it.routeTypeLabel != null && !it.routeTypeLabel.isBlank()) return it.routeTypeLabel;
                return codeToLabel(it.routeTypeCode);
            }
        }
        return null;
    }

    private @Nullable Integer labelToCode(@Nullable String label) {
        if (label == null) return null;
        switch (label.trim()) {
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

    private int parseColor(String hex) {
        return android.graphics.Color.parseColor(hex);
    }

    /** 노선유형 코드 → 대표 색상 */
    private int routeTypeColor(@Nullable Integer code) {
        if (code == null) return parseColor("#6B7280"); // 기타(회색)
        switch (code) {
            case 1: return parseColor("#0288D1"); // 공항
            case 2: return parseColor("#6A1B9A"); // 마을
            case 3: return parseColor("#1976D2"); // 간선
            case 4: return parseColor("#2E7D32"); // 지선
            case 5: return parseColor("#F9A825"); // 순환
            case 6: return parseColor("#C62828"); // 광역
            case 7: return parseColor("#1565C0"); // 인천
            case 8: return parseColor("#00695C"); // 경기
            case 9: return parseColor("#374151"); // 폐지(진회색)
            case 0: default: return parseColor("#6B7280"); // 공용/기타
        }
    }

    // ===== 팝업들 =====
    private void showBusRegisterPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_bus_register);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width  = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = android.view.Gravity.CENTER;
            dialog.getWindow().setAttributes(lp);
        }

        EditText editBusNumber = dialog.findViewById(R.id.edit_bus_number);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnRegister = dialog.findViewById(R.id.btn_register);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRegister.setOnClickListener(v -> {
            String plateNo = editBusNumber.getText().toString().trim();
            if (TextUtils.isEmpty(plateNo)) {
                Toast.makeText(this, "버스 차량번호(번호판)를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            callAssignVehicle(plateNo, dialog);
        });

        dialog.show();
    }

    private void callAssignVehicle(String plateNo, Dialog dialogToDismiss) {
        ApiService.AssignVehicleRequest body = new ApiService.AssignVehicleRequest(null, plateNo, "DRIVER_APP");
        api.assignVehicle(null, "DRIVER_APP", body)
                .enqueue(new Callback<ApiService.AssignVehicleResponse>() {
                    @Override public void onResponse(Call<ApiService.AssignVehicleResponse> call,
                                                     Response<ApiService.AssignVehicleResponse> res) {
                        if (!res.isSuccessful()) {
                            if (res.code()==409) Toast.makeText(BusListActivity.this, "운행 중에는 등록(변경) 불가. 운행 종료 후 시도하세요.", Toast.LENGTH_LONG).show();
                            else if (res.code()==404) Toast.makeText(BusListActivity.this, "차량을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                            else Toast.makeText(BusListActivity.this, "등록 실패: " + res.code(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ApiService.AssignVehicleResponse r = res.body();
                        if (r==null){ Toast.makeText(BusListActivity.this,"등록 실패(빈 응답).",Toast.LENGTH_SHORT).show(); return; }
                        saveSelectedVehicle(r.vehicleId, r.plateNo, r.routeId, r.routeName);
                        updateSelectedCard();
                        loadRegistrations();
                        loadArrivalMeta();
                        Toast.makeText(BusListActivity.this, "등록 완료: " + r.plateNo, Toast.LENGTH_SHORT).show();
                        dialogToDismiss.dismiss();
                    }
                    @Override public void onFailure(Call<ApiService.AssignVehicleResponse> call, Throwable t) {
                        Toast.makeText(BusListActivity.this, "등록 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showBusConfirmPopup() {
        if (selVehicleId == null || selPlateNo == null) {
            Toast.makeText(this, "먼저 버스를 등록해 주세요.", Toast.LENGTH_SHORT).show();
            showBusRegisterPopup();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_bus_confirm);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = android.view.Gravity.CENTER;
            dialog.getWindow().setAttributes(lp);
        }

        // 카드 모서리 클리핑
        MaterialCardView card = findFirstMaterialCardView(dialog);
        if (card != null) {
            card.setRadius(dp(18));
            card.setClipToOutline(true);
            card.setPreventCornerOverlap(false);
        }

        TextView tvRouteName  = dialog.findViewById(R.id.tvConfirmRouteName);
        TextView tvRouteType  = dialog.findViewById(R.id.tvConfirmRouteType);
        TextView tvPlate      = dialog.findViewById(R.id.tvConfirmPlate);
        ImageView ivIcon      = dialog.findViewById(R.id.ivConfirmIcon);

        tvRouteName.setText(selRouteName == null ? "-" : selRouteName);
        tvPlate.setText(selPlateNo);

        Integer code = findRouteTypeCodeInCache(selVehicleId);
        String  label = findRouteTypeLabelInCache(selVehicleId);
        if (code == null && label != null) code = labelToCode(label);
        if (label == null && code != null) label = codeToLabel(code);

        tvRouteType.setText(label == null ? "-" : label);

        int color = routeTypeColor(code);
        tvRouteName.setTextColor(color);
        tvRouteType.setTextColor(color);

        ViewCompat.setBackgroundTintList(
                tvRouteType, android.content.res.ColorStateList.valueOf(adjustAlpha(color, 0.15f))
        );
        setRoundedIconBg(ivIcon, color, 12f);

        dialog.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            dialog.dismiss();
            showSearchingPopup();
            startOperationWithLocationFlow();
        });

        dialog.show();
    }

    /** 색상 투명도 보정(칩 배경) */
    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(android.graphics.Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // 아이콘 배경 둥근 사각형
    private void setRoundedIconBg(ImageView iv, int color, float radiusDp){
        if (iv == null) return;
        ViewCompat.setBackgroundTintList(iv, null);
        ViewCompat.setBackgroundTintMode(iv, null);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(radiusDp));
        gd.setColor(color);
        gd.setStroke(1, color);

        iv.setBackground(gd);
        androidx.core.widget.ImageViewCompat.setImageTintList(
                iv, android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        );
    }

    private @Nullable MaterialCardView findFirstMaterialCardView(Dialog d){
        android.view.View content = d.findViewById(android.R.id.content);
        if (!(content instanceof android.view.ViewGroup)) return null;
        return findCardInTree((android.view.ViewGroup) ((android.view.ViewGroup)content).getChildAt(0));
    }
    private @Nullable MaterialCardView findCardInTree(@Nullable android.view.ViewGroup vg){
        if (vg == null) return null;
        if (vg instanceof MaterialCardView) return (MaterialCardView) vg;
        for (int i=0;i<vg.getChildCount();i++){
            android.view.View v = vg.getChildAt(i);
            if (v instanceof MaterialCardView) return (MaterialCardView) v;
            if (v instanceof android.view.ViewGroup){
                MaterialCardView f = findCardInTree((android.view.ViewGroup) v);
                if (f != null) return f;
            }
        }
        return null;
    }

    private float dp(float v){ return v * getResources().getDisplayMetrics().density; }

    // === 매칭 팝업 ===
    private void showSearchingPopup() {
        searchingDialog = new Dialog(this);
        searchingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        searchingDialog.setContentView(R.layout.popup_bus_search);
        if (searchingDialog.getWindow()!=null){
            searchingDialog.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            searchingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        TextView tv = searchingDialog.findViewById(R.id.subText);
        if (tv != null) tv.setText("버스와 매칭 중…");

        keepMatching = true;
        matchAttempt = 0;

        searchingDialog.findViewById(R.id.cancelButton).setOnClickListener(v->{
            keepMatching = false;
            clearPendingRetry();
            dismissSearchingPopup();
        });
        searchingDialog.setCancelable(false);
        searchingDialog.show();
    }

    private void dismissSearchingPopup() {
        if (searchingDialog!=null && searchingDialog.isShowing()) searchingDialog.dismiss();
    }

    // === 위치 + 매칭 플로우 ===
    private void startOperationWithLocationFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        fetchLocationAndStartOperation();
    }

    private void fetchLocationAndStartOperation() {
        if (currentCts != null) currentCts.cancel();
        currentCts = new CancellationTokenSource();

        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, currentCts.getToken())
                    .addOnSuccessListener(loc -> {
                        if (loc != null) onLocationReadyForStart(loc);
                        else requestSingleFreshLocation();
                    })
                    .addOnFailureListener(e -> requestSingleFreshLocation());

            // 8초 위치 타임아웃 (위치 못가져오면 팝업 닫기)
            handler.postDelayed(() -> {
                if (!isFinishing() && searchingDialog != null && searchingDialog.isShowing() && (lastLat==null || lastLon==null)) {
                    keepMatching = false;
                    dismissSearchingPopup();
                    Toast.makeText(this, "현재 위치를 가져오지 못했습니다. 잠시 후 다시 시도하세요.", Toast.LENGTH_SHORT).show();
                }
            }, 8000);

        } catch (SecurityException se) {
            keepMatching = false;
            dismissSearchingPopup();
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestSingleFreshLocation() throws SecurityException {
        LocationRequest req = new LocationRequest.Builder(0L)
                .setMinUpdateIntervalMillis(0L)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdates(1)
                .build();

        singleCallback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if (result != null && result.getLastLocation() != null) {
                    onLocationReadyForStart(result.getLastLocation());
                } else {
                    keepMatching = false;
                    dismissSearchingPopup();
                    Toast.makeText(BusListActivity.this, "현재 위치를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show();
                }
                fused.removeLocationUpdates(this);
            }
        };
        fused.requestLocationUpdates(req, singleCallback, getMainLooper());
    }

    private void onLocationReadyForStart(@Nullable Location loc) {
        if (loc == null) {
            keepMatching = false;
            dismissSearchingPopup();
            Toast.makeText(this, "현재 위치를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        lastLat = loc.getLatitude();
        lastLon = loc.getLongitude();
        matchAttempt = 0;
        tryStartOperation(lastLat, lastLon);
    }

    /** 매칭 시도 → 실패(502/404/네트워크) 시 keepMatching 동안 재시도 */
    private void tryStartOperation(Double lat, Double lon) {
        if (!keepMatching) return;

        ApiService.StartOperationRequest body = new ApiService.StartOperationRequest(lat, lon, selVehicleId);
        api.startOperation(null, "DRIVER_APP", body)
                .enqueue(new Callback<ApiService.StartOperationResponse>() {
                    @Override public void onResponse(Call<ApiService.StartOperationResponse> call,
                                                     Response<ApiService.StartOperationResponse> res) {
                        if (res.isSuccessful() && res.body()!=null) {
                            // 성공!
                            clearPendingRetry();
                            keepMatching = false;
                            dismissSearchingPopup();

                            ApiService.StartOperationResponse r = res.body();
                            saveOperationId(r.operationId);
                            tvSelectedStatus.setText("차량 상태: 정상 운행");
                            Toast.makeText(BusListActivity.this,"운행 시작! " + (r.routeName==null?"":"("+r.routeName+")"),Toast.LENGTH_SHORT).show();

                            Intent i = new Intent(BusListActivity.this, DrivingActivity.class);
                            i.putExtra("operationId", r.operationId);
                            i.putExtra("vehicleId", r.vehicleId);
                            i.putExtra("routeId", r.routeId);
                            i.putExtra("routeName", r.routeName);
                            startActivity(i); finish();
                            return;
                        }

                        // 매칭 실패 코드 → 재시도
                        if (res.code()==502 || res.code()==404) {
                            scheduleRetry();
                        } else if (res.code()==409) {
                            // 이미 운행 중
                            keepMatching = false;
                            dismissSearchingPopup();
                            Toast.makeText(BusListActivity.this,"이미 운행 중입니다. 먼저 종료하세요.",Toast.LENGTH_LONG).show();
                        } else {
                            // 기타 오류도 재시도 (원하면 여기서 중단해도 됨)
                            scheduleRetry();
                        }
                    }

                    @Override public void onFailure(Call<ApiService.StartOperationResponse> call, Throwable t) {
                        // 네트워크 오류 → 재시도
                        scheduleRetry();
                    }
                });
    }

    /** 재시도 예약 (지수 백오프 최대 5초, 3회마다 위치 새로고침) */
    private void scheduleRetry() {
        if (!keepMatching) return;
        if (matchAttempt >= MAX_ATTEMPTS) {
            keepMatching = false;
            dismissSearchingPopup();
            Toast.makeText(this, "매칭이 지연됩니다. 잠시 후 다시 시도하세요.", Toast.LENGTH_LONG).show();
            return;
        }
        matchAttempt++;
        long delay = Math.min(5000, 1000L * (long)Math.pow(1.4, matchAttempt)); // 1s → 5s
        if (searchingDialog!=null && searchingDialog.isShowing() && matchAttempt % 2 == 0) {
            Toast.makeText(this, "매칭 재시도 중… ("+matchAttempt+")", Toast.LENGTH_SHORT).show();
        }

        clearPendingRetry();
        pendingRetry = () -> {
            if (!keepMatching) return;
            // 3회마다 위치 최신화
            if (matchAttempt % 3 == 0) {
                try {
                    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                            .addOnSuccessListener(loc -> {
                                if (!keepMatching) return;
                                if (loc != null) {
                                    lastLat = loc.getLatitude();
                                    lastLon = loc.getLongitude();
                                }
                                tryStartOperation(lastLat, lastLon);
                            })
                            .addOnFailureListener(e -> tryStartOperation(lastLat, lastLon));
                } catch (SecurityException ignored) {
                    tryStartOperation(lastLat, lastLon);
                }
            } else {
                tryStartOperation(lastLat, lastLon);
            }
        };
        handler.postDelayed(pendingRetry, delay);
    }

    private void clearPendingRetry() {
        if (pendingRetry != null) {
            handler.removeCallbacks(pendingRetry);
            pendingRetry = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        keepMatching = false;
        clearPendingRetry();
        if (currentCts != null) currentCts.cancel();
        if (singleCallback != null) fused.removeLocationUpdates(singleCallback);
        handler.removeCallbacksAndMessages(null);
    }
}
