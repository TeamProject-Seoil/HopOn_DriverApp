package com.example.driver_bus_info.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.service.ApiService;

import java.util.ArrayList;
import java.util.List;

/** 등록된 버스 카드 어댑터: 1열 아이콘 / 2열 텍스트(1행: 노선명+유형, 2행: 차량번호) */
public class RegistrationAdapter extends RecyclerView.Adapter<RegistrationAdapter.VH> {

    public interface OnSelect { void onClick(ApiService.DriverVehicleRegistrationDto item); }
    public interface OnRemove { void onClick(ApiService.DriverVehicleRegistrationDto item); }

    private final OnSelect onSelect;
    private final OnRemove onRemove;

    private final List<ApiService.DriverVehicleRegistrationDto> data = new ArrayList<>();
    private String selectedVehicleId;

    private static final long CLICK_DEBOUNCE_MS = 300L;
    private long lastClickAt = 0L;

    public RegistrationAdapter(List<ApiService.DriverVehicleRegistrationDto> initial,
                               OnSelect s, OnRemove r) {
        if (initial != null) data.addAll(initial);
        this.onSelect = s;
        this.onRemove = r;
        setHasStableIds(true);
    }

    public void submit(List<ApiService.DriverVehicleRegistrationDto> d){
        data.clear();
        if (d != null) data.addAll(d);
        notifyDataSetChanged();
    }

    public void setSelectedVehicleId(String vehicleId){
        this.selectedVehicleId = vehicleId;
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        ApiService.DriverVehicleRegistrationDto it = data.get(position);
        return it.vehicleId == null ? position : it.vehicleId.hashCode();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View view = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_driver_registration_bus, p, false);
        return new VH(view);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        ApiService.DriverVehicleRegistrationDto it = data.get(i);

        // 데이터 바인딩
        h.tvRouteName.setText(safeText(it.routeName));
        h.tvPlate.setText(safeText(it.plateNo != null ? it.plateNo : it.vehicleId));

        String routeTypeLabel = (it.routeTypeLabel != null && !it.routeTypeLabel.isBlank())
                ? it.routeTypeLabel
                : codeToLabel(it.routeTypeCode);
        h.tvRouteType.setText(safeText(routeTypeLabel));

        // 유형 컬러: 노선명/노선유형 텍스트 + 아이콘 배경 틴트
        int color = routeTypeColor(it.routeTypeCode);
        h.tvRouteName.setTextColor(color);
        h.tvRouteType.setTextColor(color);

        // 아이콘은 흰색 유지, 배경만 색 변경
        ImageViewCompat.setImageTintList(h.ivBusIcon, ColorStateList.valueOf(Color.WHITE));
        ViewCompat.setBackgroundTintList(h.ivBusIcon, ColorStateList.valueOf(color));

        // 선택 하이라이트 & 배지
        boolean selected = it.vehicleId != null && it.vehicleId.equals(selectedVehicleId);
        h.card.setCardBackgroundColor(selected ? Color.parseColor("#DBEAFE") : Color.WHITE);
        h.card.setCardElevation(selected ? 8f : 4f);
        h.badgeSelected.setVisibility(selected ? View.VISIBLE : View.GONE);

        // 접근성
        String cd = "노선 " + (it.routeName == null ? "-" : it.routeName)
                + ", 유형 " + (routeTypeLabel == null ? "-" : routeTypeLabel)
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
        h.itemView.setOnClickListener(safeClick);
        h.btnRemove.setOnClickListener(safeClick);
    }

    @Override public int getItemCount() { return data.size(); }

    private static String safeText(String s) { return (s == null || s.trim().isEmpty()) ? "-" : s; }

    private static String codeToLabel(Integer code) {
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

    private static int routeTypeColor(Integer code) {
        if (code == null) return Color.parseColor("#6B7280");
        switch (code) {
            case 1: return Color.parseColor("#0288D1"); // 공항
            case 2: return Color.parseColor("#2E7D32"); // 마을
            case 3: return Color.parseColor("#1976D2"); // 간선
            case 4: return Color.parseColor("#2E7D32"); // 지선
            case 5: return Color.parseColor("#F9A825"); // 순환
            case 6: return Color.parseColor("#C62828"); // 광역
            case 7: return Color.parseColor("#1565C0"); // 인천
            case 8: return Color.parseColor("#00695C"); // 경기
            case 9: return Color.parseColor("#374151"); // 폐지
            case 0: default: return Color.parseColor("#6B7280"); // 공용/기타
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final CardView card;
        final ImageView ivBusIcon;
        final TextView tvRouteName, tvRouteType, tvPlate;
        final TextView badgeSelected;           // 선택 배지
        final ImageButton btnRemove;
        VH(@NonNull View v){
            super(v);
            card          = (CardView) v;
            ivBusIcon     = v.findViewById(R.id.ivBusIcon);
            tvRouteName   = v.findViewById(R.id.tvRouteName);
            tvRouteType   = v.findViewById(R.id.tvRouteType);
            tvPlate       = v.findViewById(R.id.tvPlate);
            badgeSelected = v.findViewById(R.id.badgeSelected);
            btnRemove     = v.findViewById(R.id.btnRemove);
        }
    }
}
