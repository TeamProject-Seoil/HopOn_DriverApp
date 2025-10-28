package com.example.driver_bus_info.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.adapter.RegistrationAdapter;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

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

    // 리스트
    private RegistrationAdapter regAdapter;

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
        tvRegEmpty = findViewById(R.id.tvRegEmpty);
        rvRegistrations = findViewById(R.id.rvRegistrations);

        // 뒤로
        btnBack.setOnClickListener(v -> { startActivity(new Intent(this, MainActivity.class)); finish(); });

        // 리스트
        regAdapter = new RegistrationAdapter(
                new ArrayList<>(),
                item -> {
                    saveSelectedVehicle(item.vehicleId, item.plateNo, item.routeId, item.routeName);
                    updateSelectedCard();
                    Toast.makeText(this, "선택됨: " + (item.plateNo == null ? item.vehicleId : item.plateNo), Toast.LENGTH_SHORT).show();
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
        updateSelectedCard();
        loadRegistrations();
    }

    // ===== 등록 이력 불러오기 =====
    private void loadRegistrations() {
        api.getDriverRegistrations(null, "DRIVER_APP")
                .enqueue(new Callback<List<ApiService.DriverVehicleRegistrationDto>>() {
                    @Override public void onResponse(Call<List<ApiService.DriverVehicleRegistrationDto>> call,
                                                     Response<List<ApiService.DriverVehicleRegistrationDto>> res) {
                        List<ApiService.DriverVehicleRegistrationDto> list =
                                res.isSuccessful() && res.body()!=null ? res.body() : new ArrayList<>();
                        regAdapter.submit(list);
                        tvRegEmpty.setVisibility(list.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
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

    private void updateSelectedCard() {
        boolean has = !TextUtils.isEmpty(selVehicleId) && !TextUtils.isEmpty(selPlateNo);
        tvSelectedRoute.setText("노선명: " + (selRouteName == null ? "-" : selRouteName));
        tvSelectedPlate.setText("차량번호: " + (selPlateNo == null ? "-" : selPlateNo));
        tvSelectedBusNumber.setText("노선 ID: " + (selRouteId == null ? "-" : selRouteId));
        tvSelectedStatus.setText(has ? "차량 상태: 선택됨(대기)" : "차량 상태: -");
    }

    // ===== 신규 등록 팝업 → assignVehicle =====
    private void showBusRegisterPopup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_bus_register);
        if (dialog.getWindow()!=null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        EditText editBusNumber = dialog.findViewById(R.id.edit_bus_number);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnRegister = dialog.findViewById(R.id.btn_register);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRegister.setOnClickListener(v -> {
            String plateNo = editBusNumber.getText().toString().trim();
            if (TextUtils.isEmpty(plateNo)) { Toast.makeText(this, "버스 차량번호(번호판)를 입력하세요.", Toast.LENGTH_SHORT).show(); return; }
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
                        Toast.makeText(BusListActivity.this, "등록 완료: " + r.plateNo, Toast.LENGTH_SHORT).show();
                        dialogToDismiss.dismiss();
                    }
                    @Override public void onFailure(Call<ApiService.AssignVehicleResponse> call, Throwable t) {
                        Toast.makeText(BusListActivity.this, "등록 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ===== 운행 시작 =====
    private void showBusConfirmPopup() {
        if (selVehicleId == null || selPlateNo == null) {
            Toast.makeText(this, "먼저 버스를 등록해 주세요.", Toast.LENGTH_SHORT).show();
            showBusRegisterPopup();
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_bus_confirm);
        if (dialog.getWindow()!=null){
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        ((TextView)dialog.findViewById(R.id.tvBusNumber)).setText(selRouteId==null?"-":selRouteId);
        ((TextView)dialog.findViewById(R.id.tvBusPlate)).setText(selPlateNo);
        ((TextView)dialog.findViewById(R.id.tvBusRoute)).setText(selRouteName==null?"-":selRouteName);
        dialog.findViewById(R.id.btn_cancel).setOnClickListener(v->dialog.dismiss());
        dialog.findViewById(R.id.btn_ok).setOnClickListener(v->{
            dialog.dismiss(); showSearchingPopup(); startOperationWithLocationFlow();
        });
        dialog.show();
    }

    private void showSearchingPopup() {
        searchingDialog = new Dialog(this);
        searchingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        searchingDialog.setContentView(R.layout.popup_bus_search);
        if (searchingDialog.getWindow()!=null){
            searchingDialog.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            searchingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        searchingDialog.findViewById(R.id.cancelButton).setOnClickListener(v->dismissSearchingPopup());
        searchingDialog.setCancelable(false);
        searchingDialog.show();
    }

    private void dismissSearchingPopup() {
        if (searchingDialog!=null && searchingDialog.isShowing()) searchingDialog.dismiss();
    }

    private void startOperationWithLocationFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION); return;
        }
        fetchLocationAndStartOperation();
    }

    private void fetchLocationAndStartOperation() {
        try {
            fused.getLastLocation()
                    .addOnSuccessListener(this::onLocationReadyForStart)
                    .addOnFailureListener(e -> { dismissSearchingPopup(); Toast.makeText(this, "위치 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show(); });
        } catch (SecurityException se) {
            dismissSearchingPopup(); Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void onLocationReadyForStart(@Nullable Location loc) {
        if (loc == null) { dismissSearchingPopup(); Toast.makeText(this, "현재 위치를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show(); return; }
        Double lat = loc.getLatitude(), lon = loc.getLongitude();
        ApiService.StartOperationRequest body = new ApiService.StartOperationRequest(lat, lon, selVehicleId);
        api.startOperation(null, "DRIVER_APP", body)
                .enqueue(new Callback<ApiService.StartOperationResponse>() {
                    @Override public void onResponse(Call<ApiService.StartOperationResponse> call,
                                                     Response<ApiService.StartOperationResponse> res) {
                        dismissSearchingPopup();
                        if (!res.isSuccessful()) {
                            if (res.code()==409) Toast.makeText(BusListActivity.this,"이미 운행 중입니다. 먼저 종료하세요.",Toast.LENGTH_LONG).show();
                            else if (res.code()==502 || res.code()==404) Toast.makeText(BusListActivity.this,"매칭 실패: 등록 차량/위치 확인.",Toast.LENGTH_LONG).show();
                            else Toast.makeText(BusListActivity.this,"운행 시작 실패: " + res.code(),Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ApiService.StartOperationResponse r = res.body();
                        if (r==null){ Toast.makeText(BusListActivity.this,"운행 시작 실패(빈 응답).",Toast.LENGTH_SHORT).show(); return; }
                        saveOperationId(r.operationId);
                        tvSelectedStatus.setText("차량 상태: 정상 운행");
                        Toast.makeText(BusListActivity.this,"운행 시작! " + (r.routeName==null?"":"("+r.routeName+")"),Toast.LENGTH_SHORT).show();
                        Intent i = new Intent(BusListActivity.this, DrivingActivity.class);
                        i.putExtra("operationId", r.operationId);
                        i.putExtra("vehicleId", r.vehicleId);
                        i.putExtra("routeId", r.routeId);
                        i.putExtra("routeName", r.routeName);
                        startActivity(i); finish();
                    }
                    @Override public void onFailure(Call<ApiService.StartOperationResponse> call, Throwable t) {
                        dismissSearchingPopup(); Toast.makeText(BusListActivity.this,"운행 시작 오류: " + t.getMessage(),Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
