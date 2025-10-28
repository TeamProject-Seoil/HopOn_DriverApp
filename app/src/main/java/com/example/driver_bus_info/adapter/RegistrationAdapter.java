package com.example.driver_bus_info.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.service.ApiService;

import java.util.List;

public class RegistrationAdapter extends RecyclerView.Adapter<RegistrationAdapter.VH> {

    public interface OnSelect { void onClick(ApiService.DriverVehicleRegistrationDto item); }
    public interface OnRemove { void onClick(ApiService.DriverVehicleRegistrationDto item); }

    private List<ApiService.DriverVehicleRegistrationDto> data;
    private final OnSelect onSelect;
    private final OnRemove onRemove;

    public RegistrationAdapter(List<ApiService.DriverVehicleRegistrationDto> data, OnSelect s, OnRemove r) {
        this.data = data; this.onSelect = s; this.onRemove = r;
    }

    public void submit(List<ApiService.DriverVehicleRegistrationDto> d){
        this.data = d; notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View view = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_driver_registration_bus, p, false);
        return new VH(view);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        ApiService.DriverVehicleRegistrationDto it = data.get(i);
        h.tvRouteName.setText(it.routeName == null ? "-" : it.routeName);
        h.tvPlate.setText(it.plateNo != null ? it.plateNo : it.vehicleId);
        h.tvRouteId.setText(it.routeId == null ? "-" : it.routeId);

        h.itemView.setOnClickListener(v -> onSelect.onClick(it)); // 카드 전체 = 선택
        h.btnRemove.setOnClickListener(v -> onRemove.onClick(it)); // X = 제거
    }

    @Override public int getItemCount() { return data == null ? 0 : data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvRouteName, tvPlate, tvRouteId;
        ImageButton btnRemove;
        VH(@NonNull View v){
            super(v);
            tvRouteName = v.findViewById(R.id.tvRouteName);
            tvPlate     = v.findViewById(R.id.tvPlate);
            tvRouteId   = v.findViewById(R.id.tvRouteId);
            btnRemove   = v.findViewById(R.id.btnRemove);
        }
    }
}

