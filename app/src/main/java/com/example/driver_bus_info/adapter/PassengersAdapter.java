// app/src/main/java/com/example/driver_bus_info/adapter/PassengersAdapter.java
package com.example.driver_bus_info.adapter;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.driver_bus_info.R;
import com.example.driver_bus_info.service.ApiService;
import java.util.*;

public class PassengersAdapter extends RecyclerView.Adapter<PassengersAdapter.VH> {

    private final List<ApiService.DriverPassengerDto> items = new ArrayList<>();

    public void submit(List<ApiService.DriverPassengerDto> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_passenger_row, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(VH h, int pos) {
        ApiService.DriverPassengerDto d = items.get(pos);
        h.tvName.setText(nz(d.username, d.userid, "-"));
        h.tvStatus.setText(toKorean(d.status));
        h.tvBoard.setText(nz(d.boardingStopName, "-"));
        h.tvAlight.setText(nz(d.alightingStopName, "-"));

        // 색상(선택): BOARDED = 파랑, CONFIRMED = 회색
        int color = "BOARDED".equals(d.status) ? Color.parseColor("#1976D2") : Color.parseColor("#6B7280");
        h.tvStatus.setTextColor(color);
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus, tvBoard, tvAlight;
        VH(View v) {
            super(v);
            tvName   = v.findViewById(R.id.tvPaxName);
            tvStatus = v.findViewById(R.id.tvPaxStatus);
            tvBoard  = v.findViewById(R.id.tvBoardStop);
            tvAlight = v.findViewById(R.id.tvAlightStop);
        }
    }

    private static String nz(String a, String b, String d){
        if (!TextUtils.isEmpty(a)) return a;
        if (!TextUtils.isEmpty(b)) return b;
        return d;
    }
    private static String nz(String a, String d){ return TextUtils.isEmpty(a) ? d : a; }
    private static String toKorean(String st){
        if ("BOARDED".equals(st)) return "탑승";
        if ("CONFIRMED".equals(st)) return "예약";
        return st != null ? st : "-";
    }
}
