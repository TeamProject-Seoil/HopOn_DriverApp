package com.example.driver_bus_info.activity;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.adapter.NoticeAdapter;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 공지 화면 */
public class NoticeActivity extends AppCompatActivity {

    private ImageButton backBtn;
    private Button btnAll, btnInfo, btnUpdate, btnMaint;
    // 2차 필터(읽음)
    private Button btnReadAll, btnReadOnly, btnUnreadOnly;

    private RecyclerView recycler;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe;

    private NoticeAdapter adapter;
    private ApiService api;

    @Nullable private String currentType = null; // null=전체
    @Nullable private Boolean readFilter = null; // null=전체, true=읽음, false=안읽음

    private int page = 0, size = 20;
    private boolean loading = false, last = false;

    private long countAll = 0L, countInfo = 0L, countUpdate = 0L, countMaint = 0L;
    private long unreadTotal = 0L; // 전체 안읽음 수(역할 기준). 읽음 수는 countAll - unreadTotal.

    private TokenManager tm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice);

        api = ApiClient.get(getApplicationContext());
        tm  = TokenManager.get(this);

        backBtn   = findViewById(R.id.notice_back_button);
        btnAll    = findViewById(R.id.btn_all);
        btnInfo   = findViewById(R.id.btn_info);
        btnUpdate = findViewById(R.id.btn_update);
        btnMaint  = findViewById(R.id.btn_maint);

        btnReadAll    = findViewById(R.id.btn_read_all);
        btnReadOnly   = findViewById(R.id.btn_read_only);
        btnUnreadOnly = findViewById(R.id.btn_unread_only);

        recycler  = findViewById(R.id.recycler);
        swipe     = findViewById(R.id.swipe);

        backBtn.setOnClickListener(v -> finish());

        adapter = new NoticeAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        recycler.setItemAnimator(animator);
        recycler.setAdapter(adapter);

        // 카드 펼칠 때: 읽음 처리 → 상세조회(뷰카운트) → UI갱신
        adapter.setOnItemToggle((notice, expanded, pos) -> {
            if (!expanded) return;

            String bearer = buildBearer();
            if (bearer == null) {
                Toast.makeText(this, "로그인이 필요합니다(읽음 처리 불가)", Toast.LENGTH_SHORT).show();
                return;
            }

            api.markNoticeRead(bearer, notice.id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> resp) {
                    // 1) UI에서 즉시 읽음 상태 반영
                    adapter.setItemRead(notice.id, true);

                    // 2) 현재 2차 필터가 '안읽음'이면 리스트에서 바로 제거
                    if (Boolean.FALSE.equals(readFilter)) {
                        adapter.removeAt(pos);
                        if (!last && !loading) { loadNextPage(); }
                    }

                    // 3) 조회수 증가 호출
                    api.getNoticeDetail(bearer, notice.id, true)
                            .enqueue(new Callback<ApiService.NoticeResp>() {
                                @Override public void onResponse(Call<ApiService.NoticeResp> call, Response<ApiService.NoticeResp> r) { }
                                @Override public void onFailure (Call<ApiService.NoticeResp> call, Throwable t) { }
                            });

                    // 4) 상단 안읽음 카운트 갱신
                    refreshUnreadCountOnly();
                }

                @Override public void onFailure(Call<Void> call, Throwable t) {
                    api.getNoticeDetail(bearer, notice.id, true)
                            .enqueue(new Callback<ApiService.NoticeResp>() {
                                @Override public void onResponse(Call<ApiService.NoticeResp> call, Response<ApiService.NoticeResp> r) { }
                                @Override public void onFailure (Call<ApiService.NoticeResp> call, Throwable tt) { }
                            });
                }
            });
        });

        // 1차 필터(유형)
        btnAll.setOnClickListener(v -> selectType(null));
        btnInfo.setOnClickListener(v -> selectType("INFO"));
        btnUpdate.setOnClickListener(v -> selectType("UPDATE"));
        btnMaint.setOnClickListener(v -> selectType("MAINTENANCE"));

        // 2차 필터(읽음)
        btnReadAll.setOnClickListener(v -> { selectReadFilter(null); });
        btnReadOnly.setOnClickListener(v -> { selectReadFilter(Boolean.TRUE); });
        btnUnreadOnly.setOnClickListener(v -> { selectReadFilter(Boolean.FALSE); });

        swipe.setOnRefreshListener(() -> {
            refreshCounts();
            reloadFirstPage();
        });

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int lastPos = lm.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                if (!loading && !last && lastPos >= total - 3) {
                    loadNextPage();
                }
            }
        });

        updateFilterButtonTexts();
        styleFilterButtons();
        styleReadFilterButtons();

        refreshCounts();
        selectType(null);
        selectReadFilter(null);
    }

    private String buildBearer() {
        if (tm == null || tm.accessToken() == null) return null;
        String type = tm.tokenType() != null ? tm.tokenType() : "Bearer";
        return type + " " + tm.accessToken();
    }

    private void selectType(@Nullable String type) {
        currentType = type;
        styleFilterButtons();
        reloadFirstPage();
    }

    private void selectReadFilter(@Nullable Boolean rf) {
        readFilter = rf; // null=전체, true=읽음, false=안읽음
        styleReadFilterButtons();
        reloadFirstPage();
    }

    private void updateFilterButtonTexts() {
        btnAll.setText("전체(" + countAll + ")");
        btnInfo.setText("공지(" + countInfo + ")");
        btnUpdate.setText("업데이트(" + countUpdate + ")");
        btnMaint.setText("점검(" + countMaint + ")");

        long readCnt = Math.max(0, countAll - unreadTotal);
        btnReadAll.setText("전체(" + countAll + ")");
        btnReadOnly.setText("읽음(" + readCnt + ")");
        btnUnreadOnly.setText("안읽음(" + unreadTotal + ")");
    }

    private void styleFilterButtons() {
        setBtnStyle(btnAll,   currentType == null);
        setBtnStyle(btnInfo,  "INFO".equals(currentType));
        setBtnStyle(btnUpdate,"UPDATE".equals(currentType));
        setBtnStyle(btnMaint, "MAINTENANCE".equals(currentType));
        btnAll.setBackgroundTintList((ColorStateList) null);
        btnInfo.setBackgroundTintList((ColorStateList) null);
        btnUpdate.setBackgroundTintList((ColorStateList) null);
        btnMaint.setBackgroundTintList((ColorStateList) null);
    }

    private void styleReadFilterButtons() {
        setBtnStyleSoft(btnReadAll,    readFilter == null);
        setBtnStyleSoft(btnReadOnly,   Boolean.TRUE.equals(readFilter));
        setBtnStyleSoft(btnUnreadOnly, Boolean.FALSE.equals(readFilter));

        btnReadAll.setBackgroundTintList((ColorStateList) null);
        btnReadOnly.setBackgroundTintList((ColorStateList) null);
        btnUnreadOnly.setBackgroundTintList((ColorStateList) null);
    }

    private void setBtnStyle(Button b, boolean on) {
        if (on) { b.setBackgroundResource(R.drawable.bg_filter_blue); b.setTextColor(0xFF1A73E8); }
        else    { b.setBackgroundResource(R.drawable.bg_filter_white); b.setTextColor(0xFF222222); }
    }
    private void setBtnStyleSoft(Button b, boolean on) {
        if (on) { b.setBackgroundResource(R.drawable.bg_filter_blue_soft); b.setTextColor(0xFF1A73E8); }
        else    { b.setBackgroundResource(R.drawable.bg_filter_white);     b.setTextColor(0xFF222222); }
    }

    private void reloadFirstPage() { page = 0; last = false; requestPage(true); }
    private void loadNextPage()    { if (!last && !loading) { page++; requestPage(false); } }

    private void requestPage(boolean clear) {
        if (loading) return;
        loading = true;
        if (clear && !swipe.isRefreshing()) swipe.setRefreshing(true);

        final String sort = "updatedAt,desc";
        final String q    = null; // 검색어
        final String type = currentType; // INFO/UPDATE/MAINTENANCE 또는 null

        api.getNotices(page, size, sort, q, type)
                .enqueue(new Callback<ApiService.PageResponse<ApiService.NoticeResp>>() {
                    @Override
                    public void onResponse(Call<ApiService.PageResponse<ApiService.NoticeResp>> call,
                                           Response<ApiService.PageResponse<ApiService.NoticeResp>> resp) {
                        loading = false;
                        swipe.setRefreshing(false);
                        if (!resp.isSuccessful() || resp.body() == null) {
                            Toast.makeText(NoticeActivity.this, "불러오기 실패", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ApiService.PageResponse<ApiService.NoticeResp> body = resp.body();

                        // 2차 필터(읽음/안읽음) — 공통 isRead()로 후처리
                        List<ApiService.NoticeResp> display = body.content;
                        if (readFilter != null && display != null) {
                            List<ApiService.NoticeResp> filtered = new ArrayList<>();
                            for (ApiService.NoticeResp n : display) {
                                boolean r = NoticeAdapter.isRead(n);
                                if (readFilter.equals(r)) filtered.add(n);
                            }
                            display = filtered;
                        }

                        adapter.setData(display, clear);
                        last = body.last;
                    }

                    @Override
                    public void onFailure(Call<ApiService.PageResponse<ApiService.NoticeResp>> call, Throwable t) {
                        loading = false;
                        swipe.setRefreshing(false);
                        Toast.makeText(NoticeActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** 각 유형별 전체 개수 + 전체 안읽음 개수 */
    private void refreshCounts() {
        final String sort = "updatedAt,desc";
        final String q    = null;

        api.getNotices(0, 1, sort, q, null)
                .enqueue(new CountCb(v -> { countAll = v; updateFilterButtonTexts(); }));
        api.getNotices(0, 1, sort, q, "INFO")
                .enqueue(new CountCb(v -> { countInfo = v; updateFilterButtonTexts(); }));
        api.getNotices(0, 1, sort, q, "UPDATE")
                .enqueue(new CountCb(v -> { countUpdate = v; updateFilterButtonTexts(); }));
        api.getNotices(0, 1, sort, q, "MAINTENANCE")
                .enqueue(new CountCb(v -> { countMaint = v; updateFilterButtonTexts(); }));

        refreshUnreadCountOnly();
    }

    private void refreshUnreadCountOnly() {
        String bearer = buildBearer();
        if (bearer == null) { unreadTotal = 0L; updateFilterButtonTexts(); return; }
        api.getUnreadNoticeCount(bearer).enqueue(new Callback<Map<String, Integer>>() {
            @Override public void onResponse(Call<Map<String, Integer>> call, Response<Map<String, Integer>> res) {
                if (res.isSuccessful() && res.body() != null) {
                    Integer c = res.body().get("count");
                    if (c == null) c = res.body().get("unreadCount");
                    unreadTotal = c == null ? 0L : c.longValue();
                } else {
                    unreadTotal = 0L;
                }
                updateFilterButtonTexts();
            }
            @Override public void onFailure(Call<Map<String, Integer>> call, Throwable t) {
                unreadTotal = 0L;
                updateFilterButtonTexts();
            }
        });
    }

    private static class CountCb implements Callback<ApiService.PageResponse<ApiService.NoticeResp>> {
        interface S { void apply(long v); }
        private final S setter;
        CountCb(S s) { this.setter = s; }
        @Override public void onResponse(Call<ApiService.PageResponse<ApiService.NoticeResp>> call,
                                         Response<ApiService.PageResponse<ApiService.NoticeResp>> resp) {
            long v = 0L;
            if (resp.isSuccessful() && resp.body()!=null) v = resp.body().totalElements;
            setter.apply(v);
        }
        @Override public void onFailure(Call<ApiService.PageResponse<ApiService.NoticeResp>> call, Throwable t) {
            setter.apply(0L);
        }
    }
}
