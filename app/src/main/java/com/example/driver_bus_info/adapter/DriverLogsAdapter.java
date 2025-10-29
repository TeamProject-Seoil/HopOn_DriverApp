// app/src/main/java/com/example/driver_bus_info/adapter/DriverLogsAdapter.java
package com.example.driver_bus_info.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.service.ApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DriverLogsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_DATE = 1;
    private static final int TYPE_ITEM = 2;

    /** 화면에 뿌릴 행(날짜 구분/아이템)을 섞어 넣는 리스트 */
    private final List<Row> rows = new ArrayList<>();

    /** 서버에서 받아온 아이템을 최신 종료순으로 가정하고, 날짜가 바뀌면 구분 행 삽입 */
    public void submit(List<ApiService.DriverOperationListItem> newItems, boolean clear) {
        if (clear) rows.clear();
        if (newItems != null && !newItems.isEmpty()) {
            String prevDate = null;
            for (ApiService.DriverOperationListItem it : newItems) {
                String date = dayKey(it.endedAt != null ? it.endedAt : it.startedAt); // 종료일 기준, 없으면 시작일
                if (date == null) date = "-";
                if (!TextUtils.equals(prevDate, date)) {
                    rows.add(Row.date(date));
                    prevDate = date;
                }
                rows.add(Row.item(it));
            }
        }
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_DATE) {
            View v = inf.inflate(R.layout.item_driver_log_date, parent, false);
            return new DateVH(v);
        } else {
            View v = inf.inflate(R.layout.item_driver_log, parent, false);
            return new ItemVH(v);
        }
    }

    @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
        Row row = rows.get(pos);
        if (row.type == TYPE_DATE) {
            DateVH d = (DateVH) h;
            d.tvDate.setText(row.dateLabel);
            return;
        }

        ItemVH v = (ItemVH) h;
        ApiService.DriverOperationListItem it = row.item;

        // 캡션 라벨은 XML에 고정되어 있으므로 값만 세팅
        v.tvRouteName.setText(nullToDash(it.routeName));
        v.tvPlateNo.setText(nullToDash(it.plateNo));
        v.tvStart.setText(shortTime(it.startedAt));
        v.tvEnd.setText(shortTime(it.endedAt));
        v.tvDuration.setText(calcDuration(it.startedAt, it.endedAt));

        // 라벨/코드 → 칩/아이콘 색
        Integer code = it.routeTypeCode != null ? it.routeTypeCode : labelToCode(it.routeTypeLabel);
        String  label= !TextUtils.isEmpty(it.routeTypeLabel) ? it.routeTypeLabel : codeToLabel(code);
        v.tvRouteType.setText(TextUtils.isEmpty(label) ? "-" : label);

        int color = routeTypeColor(code);
        v.tvRouteType.setTextColor(color);
        v.tvRouteType.setBackgroundTintList(ColorStateList.valueOf(adjustAlpha(color, 0.15f)));
        tintCircle(v.ivRouteIcon, color);
        tintCircle(v.ivPlateIcon, color);
    }

    @Override public int getItemCount() { return rows.size(); }

    // --- ViewHolders ---
    static class DateVH extends RecyclerView.ViewHolder {
        TextView tvDate;
        DateVH(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDateSeparator);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        ImageView ivRouteIcon, ivPlateIcon;
        TextView tvRouteName, tvRouteType, tvPlateNo, tvStart, tvEnd, tvDuration;
        // 캡션은 XML에 상주(TextView)지만 코드로 바꿀 건 없음
        ItemVH(View itemView) {
            super(itemView);
            ivRouteIcon = itemView.findViewById(R.id.ivRouteIcon);
            tvRouteName = itemView.findViewById(R.id.tvRouteName);
            tvRouteType = itemView.findViewById(R.id.tvRouteType);
            tvPlateNo   = itemView.findViewById(R.id.tvPlateNo);
            tvStart     = itemView.findViewById(R.id.tvStartTime);
            tvEnd       = itemView.findViewById(R.id.tvEndTime);
            tvDuration  = itemView.findViewById(R.id.tvDuration);
        }
    }

    // --- internal row model ---
    private static class Row {
        final int type;
        final String dateLabel;
        final ApiService.DriverOperationListItem item;
        private Row(int type, String dateLabel, ApiService.DriverOperationListItem item) {
            this.type = type; this.dateLabel = dateLabel; this.item = item;
        }
        static Row date(String label){ return new Row(TYPE_DATE, label, null); }
        static Row item(ApiService.DriverOperationListItem it){ return new Row(TYPE_ITEM, null, it); }
    }

    // ---- utils ----
    private static void tintCircle(ImageView iv, int color){
        if (iv == null) return;
        iv.setBackgroundTintList(ColorStateList.valueOf(color));
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(Color.WHITE));
    }
    private static String nullToDash(String s){ return TextUtils.isEmpty(s) ? "-" : s; }

    /** ISO → "HH:mm" 혹은 이미 "HH:mm"이면 그대로 */
    private static String shortTime(String isoOrHm){
        if (TextUtils.isEmpty(isoOrHm)) return "--:--";
        if (isoOrHm.length() >= 16 && isoOrHm.contains("T")){
            try { return isoOrHm.substring(11, 16);} catch(Exception ignore){}
        }
        if (isoOrHm.length() >= 5 && isoOrHm.charAt(2) == ':' ) return isoOrHm.substring(0,5);
        return isoOrHm;
    }

    /** 종료/시작 ISO → "yyyy.MM.dd" KST */
    private static String dayKey(String iso){
        if (TextUtils.isEmpty(iso)) return null;
        // 빠르고 안전하게 substring (yyyy-MM-dd... or yyyy-MM-ddTHH...)
        try {
            String y = iso.substring(0,4);
            String m = iso.substring(5,7);
            String d = iso.substring(8,10);
            return String.format(Locale.KOREA, "%s.%s.%s", y, m, d);
        } catch (Exception ignore) { return null; }
    }

    private static String calcDuration(String started, String ended){
        try{
            if (TextUtils.isEmpty(started) || TextUtils.isEmpty(ended)) return "-";
            String s = shortTime(started);
            String e = shortTime(ended);
            if (s.length() < 5 || e.length() < 5) return "-";
            int sh = Integer.parseInt(s.substring(0,2));
            int sm = Integer.parseInt(s.substring(3,5));
            int eh = Integer.parseInt(e.substring(0,2));
            int em = Integer.parseInt(e.substring(3,5));
            int startMin = sh*60+sm, endMin = eh*60+em;
            int diff = Math.max(0, endMin - startMin);
            int hh = diff/60, mm = diff%60;
            return String.format(Locale.KOREA, "%02d:%02d", hh, mm);
        }catch(Exception ex){ return "-"; }
    }

    private static Integer labelToCode(String label){
        if (label == null) return null;
        String t = label.trim();
        switch (t){
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
    private static String codeToLabel(Integer code){
        if (code == null) return null;
        switch (code){
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
            default: return null;
        }
    }
    private static int routeTypeColor(Integer code){
        if (code == null) return Color.parseColor("#6B7280");
        switch (code){
            case 1: return Color.parseColor("#0288D1"); // 공항
            case 2: return Color.parseColor("#6A1B9A"); // 마을
            case 3: return Color.parseColor("#1976D2"); // 간선
            case 4: return Color.parseColor("#2E7D32"); // 지선
            case 5: return Color.parseColor("#F9A825"); // 순환
            case 6: return Color.parseColor("#C62828"); // 광역
            case 7: return Color.parseColor("#1565C0"); // 인천
            case 8: return Color.parseColor("#00695C"); // 경기
            case 9: return Color.parseColor("#374151"); // 폐지
            case 0: return Color.parseColor("#6B7280"); // 공용
            default: return Color.parseColor("#6B7280");
        }
    }
    private static int adjustAlpha(int color, float factor){
        int alpha = Math.round(Color.alpha(color)*factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
