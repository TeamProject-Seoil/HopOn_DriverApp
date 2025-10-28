package com.example.driver_bus_info.adapter;

import android.graphics.Color;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.service.ApiService;

import java.util.ArrayList;
import java.util.List;

/**
 * 등록된 버스 카드 리스트 어댑터
 * - 2열 레이아웃(item_driver_registration_bus.xml)에 맞춘 바인딩
 * - 선택 항목 하이라이트
 * - 클릭 디바운스(연타 방지)
 * - 접근성(ContentDescription) 보완
 */
public class RegistrationAdapter extends RecyclerView.Adapter<RegistrationAdapter.VH> {

    public interface OnSelect { void onClick(ApiService.DriverVehicleRegistrationDto item); }
    public interface OnRemove { void onClick(ApiService.DriverVehicleRegistrationDto item); }

    private final OnSelect onSelect;
    private final OnRemove onRemove;

    private final List<ApiService.DriverVehicleRegistrationDto> data = new ArrayList<>();

    // 선택 하이라이트용
    private String selectedVehicleId;

    // 클릭 디바운스
    private static final long CLICK_DEBOUNCE_MS = 300L;
    private long lastClickAt = 0L;

    public RegistrationAdapter(List<ApiService.DriverVehicleRegistrationDto> initial,
                               OnSelect s, OnRemove r) {
        if (initial != null) data.addAll(initial);
        this.onSelect = s;
        this.onRemove = r;
        setHasStableIds(true);
    }

    /** 전체 리스트 교체 */
    public void submit(List<ApiService.DriverVehicleRegistrationDto> d){
        data.clear();
        if (d != null) data.addAll(d);
        notifyDataSetChanged();
    }

    /** 액티비티에서 선택 저장 후 호출해주면 카드 하이라이트됨 */
    public void setSelectedVehicleId(String vehicleId){
        this.selectedVehicleId = vehicleId;
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        ApiService.DriverVehicleRegistrationDto it = data.get(position);
        // vehicleId가 null일 수 있으니 position fallback
        return it.vehicleId == null ? position : it.vehicleId.hashCode();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View view = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_driver_registration_bus, p, false);
        return new VH(view);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        ApiService.DriverVehicleRegistrationDto it = data.get(i);

        // 값 바인딩
        h.tvRouteName.setText(safeText(it.routeName));                       // 좌측 상단
        h.tvPlate.setText(safeText(it.plateNo != null ? it.plateNo : it.vehicleId)); // 좌측 하단
        h.tvRouteId.setText(safeText(it.routeId));                           // 우측 상단
        h.tvVehicleId.setText(safeText(it.vehicleId));                       // 우측 하단

        // 선택 하이라이트
        boolean selected = it.vehicleId != null && it.vehicleId.equals(selectedVehicleId);
        h.card.setCardBackgroundColor(selected ? Color.parseColor("#DBEAFE") : Color.WHITE);
        h.card.setCardElevation(selected ? 8f : 4f);

        // 접근성: 카드 요약
        String cd = "노선 " + (it.routeName == null ? "-" : it.routeName)
                + ", 차량번호 " + (it.plateNo == null ? (it.vehicleId == null ? "-" : it.vehicleId) : it.plateNo);
        h.itemView.setContentDescription(cd);

        // 클릭 디바운스
        View.OnClickListener safeClick = v -> {
            long now = SystemClock.elapsedRealtime();
            if (now - lastClickAt < CLICK_DEBOUNCE_MS) return;
            lastClickAt = now;

            if (v.getId() == R.id.btnRemove) {
                if (onRemove != null) onRemove.onClick(it);
            } else {
                if (onSelect != null) onSelect.onClick(it);
            }
        };

        h.itemView.setOnClickListener(safeClick);   // 카드 전체 = 선택
        h.btnRemove.setOnClickListener(safeClick);  // X 버튼 = 제거
    }

    @Override public int getItemCount() { return data.size(); }

    private static String safeText(String s) { return (s == null || s.trim().isEmpty()) ? "-" : s; }

    static class VH extends RecyclerView.ViewHolder {
        final CardView card;
        final TextView tvRouteName, tvPlate, tvRouteId, tvVehicleId;
        final ImageButton btnRemove;
        VH(@NonNull View v){
            super(v);
            // 루트가 CardView라는 전제
            card        = (CardView) v;
            tvRouteName = v.findViewById(R.id.tvRouteName);
            tvPlate     = v.findViewById(R.id.tvPlate);
            tvRouteId   = v.findViewById(R.id.tvRouteId);
            tvVehicleId = v.findViewById(R.id.tvVehicleId);
            btnRemove   = v.findViewById(R.id.btnRemove);
        }
    }
}
