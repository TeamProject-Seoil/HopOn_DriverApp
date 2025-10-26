package com.example.driver_bus_info.adapter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.service.ApiService;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.VH> {

    public interface OnItemToggle {
        void onToggle(ApiService.NoticeResp item, boolean expanded, int adapterPosition);
    }

    private OnItemToggle toggleListener;
    public void setOnItemToggle(OnItemToggle l) { this.toggleListener = l; }

    private final List<ApiService.NoticeResp> items = new ArrayList<>();
    private final Set<Long> expandedIds = new HashSet<>();

    public NoticeAdapter() { setHasStableIds(true); }

    public void setData(List<ApiService.NoticeResp> list, boolean clear) {
        if (clear) { items.clear(); expandedIds.clear(); }
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    /** 공통 읽음 판정: readAt → unread → read 순서로 평가 */
    public static boolean isRead(ApiService.NoticeResp n) {
        if (n == null) return false;
        if (n.readAt != null && !n.readAt.isEmpty()) return true;
        if (n.unread != null) return !n.unread;
        if (n.read != null)   return n.read;
        return false;
    }

    /** 읽음 상태 갱신(목록도 즉시 반영) */
    public void setItemRead(long id, boolean read) {
        for (int i = 0; i < items.size(); i++) {
            ApiService.NoticeResp n = items.get(i);
            if (n != null && n.id != null && n.id == id) {
                n.read   = read;
                n.unread = !read;
                if (read) n.readAt = (n.readAt == null) ? "now" : n.readAt; // 표기용 더미 타임스탬프
                notifyItemChanged(i);
                break;
            }
        }
    }

    /** 현재 리스트에서 position 항목 제거 (안읽음 탭에서 읽음 처리 시 즉시 제거용) */
    public void removeAt(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
    }

    @Override public long getItemId(int position) {
        ApiService.NoticeResp n = items.get(position);
        return (n != null && n.id != null) ? n.id : RecyclerView.NO_ID;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notice_card, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        ApiService.NoticeResp n = items.get(pos);
        boolean isOpen = n.id != null && expandedIds.contains(n.id);

        cancelAnim(h.content);

        h.title.setText(s(n.title));
        h.content.setText(s(n.content));
        h.badge.setText(mapTypeLabel(n.noticeType));
        h.date.setText(formatDateDot(n.createdAt));
        h.viewCount.setText("조회수 " + formatCount(n.viewCount));

        // 읽음/안읽음 비주얼 (공통 판정 사용)
        boolean read = isRead(n);
        h.readDot.setVisibility(read ? View.INVISIBLE : View.VISIBLE);
        h.title.setTextColor(read ? 0xFF222222 : 0xFF111111);
        h.title.setTypeface(h.title.getTypeface(),
                read ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);

        @ColorInt int selectedBg = 0xFFE8F0FE;
        @ColorInt int white = 0xFFFFFFFF;

        h.container.setBackgroundColor(isOpen ? selectedBg : white);
        h.content.setBackgroundColor(white);
        h.content.setAlpha(1f);
        h.content.setVisibility(isOpen ? View.VISIBLE : View.GONE);
        h.arrow.setRotation(isOpen ? 180f : 0f);

        h.header.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION) return;

            ApiService.NoticeResp cur = items.get(p);
            if (cur == null || cur.id == null) return;

            cancelAnim(h.content);

            boolean nowOpen = !expandedIds.contains(cur.id);
            if (nowOpen) expandedIds.add(cur.id);
            else         expandedIds.remove(cur.id);

            h.container.setBackgroundColor(nowOpen ? selectedBg : white);
            h.arrow.animate().rotation(nowOpen ? 180f : 0f).setDuration(160).start();

            if (nowOpen) {
                h.content.setBackgroundColor(white);
                h.content.setAlpha(0f);
                h.content.setVisibility(View.VISIBLE);
                h.content.animate().alpha(1f).setDuration(160).setListener(null).start();
            } else {
                h.content.animate().alpha(0f).setDuration(140)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override public void onAnimationEnd(Animator animation) {
                                h.content.setVisibility(View.GONE);
                                h.content.setAlpha(1f);
                                h.content.animate().setListener(null);
                            }
                        }).start();
            }

            if (toggleListener != null && nowOpen) {
                toggleListener.onToggle(cur, true, p);
            }
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        View container, header;
        View readDot;
        TextView badge, title, date, viewCount, content;
        ImageView arrow;
        VH(@NonNull View v) {
            super(v);
            card = (CardView) v;
            container = v.findViewById(R.id.container);
            header = v.findViewById(R.id.header);
            readDot = v.findViewById(R.id.read_dot);
            badge = v.findViewById(R.id.badge);
            title = v.findViewById(R.id.title);
            date  = v.findViewById(R.id.date);
            viewCount = v.findViewById(R.id.view_count);
            content = v.findViewById(R.id.content);
            arrow = v.findViewById(R.id.arrow);
        }
    }

    private static void cancelAnim(View v) {
        if (v == null) return;
        ViewPropertyAnimator a = v.animate();
        if (a != null) a.cancel();
        v.clearAnimation();
        v.animate().setListener(null);
    }

    private static String s(String x) { return x == null ? "" : x; }

    private static String mapTypeLabel(String type) {
        if (type == null) return "공지";
        switch (type) {
            case "INFO": return "공지";
            case "UPDATE": return "업데이트";
            case "MAINTENANCE": return "점검";
            default: return "공지";
        }
    }

    private static String formatDateDot(String iso) {
        if (TextUtils.isEmpty(iso) || iso.length() < 10) return "";
        return iso.substring(0, 10).replace("-", ".");
    }

    private static String formatCount(long v) {
        if (v < 0) v = 0;
        return NumberFormat.getInstance(Locale.getDefault()).format(v);
    }
}
