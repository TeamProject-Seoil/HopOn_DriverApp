package com.example.driver_bus_info.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.adapter.InquiryAdapter;
import com.example.driver_bus_info.core.TokenManager;           // ✅ TokenManager 사용
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;

import java.util.Collections;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InquiryActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private Button btnAll, btnOpen, btnAnswered, btnClosed, btnMineToggle, btnWrite;
    private RecyclerView recycler;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe;
    private LinearLayout viewLoginHint;
    private LinearLayout rowMine;

    private InquiryAdapter adapter;

    private TokenManager tm;                          // ✅
    @Nullable private String userId = null;
    @Nullable private String email  = null;
    @Nullable private String role   = null;
    @Nullable private String bearer = null;
    private String clientType;

    @Nullable private String curStatus = null;
    private int page = 0, size = 20;
    private boolean loading = false, last = false;

    private long countAll=0, countOpen=0, countAnswered=0, countClosed=0;
    private boolean mineOnly = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry);

        btnBack       = findViewById(R.id.btn_back);
        btnAll        = findViewById(R.id.btn_all);
        btnOpen       = findViewById(R.id.btn_open);
        btnAnswered   = findViewById(R.id.btn_answered);
        btnClosed     = findViewById(R.id.btn_closed);
        btnMineToggle = findViewById(R.id.btn_mine);
        btnWrite      = findViewById(R.id.btn_write);
        recycler      = findViewById(R.id.recycler);
        swipe         = findViewById(R.id.swipe);
        viewLoginHint = findViewById(R.id.view_login_hint);
        rowMine       = findViewById(R.id.row_mine);

        tm = TokenManager.get(this);                  // ✅ 통일
        clientType = DeviceInfo.getClientType();

        btnBack.setOnClickListener(v -> finish());

        adapter = new InquiryAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        btnAll.setOnClickListener(v -> selectStatus(null));
        btnOpen.setOnClickListener(v -> selectStatus("OPEN"));
        btnAnswered.setOnClickListener(v -> selectStatus("ANSWERED"));
        btnClosed.setOnClickListener(v -> selectStatus("CLOSED"));

        btnMineToggle.setOnClickListener(v -> {
            if (TextUtils.isEmpty(userId)) {
                Toast.makeText(this, "로그인 후 사용 가능합니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            mineOnly = !mineOnly;
            adapter.setPublicMode(!mineOnly);
            styleButtons();
            if (mineOnly) refreshCounts(); else refreshCountsPublic();
            reloadFirst();
        });

        swipe.setOnRefreshListener(this::reloadFirst);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy<=0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int lastPos = lm.findLastVisibleItemPosition();
                int total   = adapter.getItemCount();
                if (!loading && !last && lastPos >= total-3) loadNext();
            }
        });

        btnWrite.setOnClickListener(v ->
                startActivity(new Intent(this, InquiryComposeActivity.class)));

        curStatus = null;
        styleButtons();
        applyFilterButtonText();

        initByAuth();
    }

    @Override protected void onResume() {
        super.onResume();
        reloadFirst();
    }

    /** 로그인 여부/유저 정보 초기화 */
    private void initByAuth() {
        String access = tm.accessToken();                 // ✅ TokenManager에서 읽기
        if (TextUtils.isEmpty(access)) {
            // 비로그인
            bearer = null;
            userId = email = role = null;
            mineOnly = false;

            adapter.setPublicMode(true);

            if (rowMine != null) rowMine.setVisibility(View.GONE);
            btnMineToggle.setVisibility(View.GONE);
            viewLoginHint.setVisibility(View.VISIBLE);

            refreshCountsPublic();
            selectStatus(null);
            return;
        }

        bearer = (tm.tokenType() != null ? tm.tokenType() : "Bearer") + " " + access;

        ApiClient.get(getApplicationContext())
                .me(bearer, clientType)
                .enqueue(new Callback<ApiService.UserResponse>() {
                    @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                        if (!res.isSuccessful() || res.body()==null) {
                            // 실패 → 비로그인 UI
                            bearer = null;
                            userId = email = role = null;
                            mineOnly = false;
                            adapter.setPublicMode(true);

                            if (rowMine != null) rowMine.setVisibility(View.GONE);
                            btnMineToggle.setVisibility(View.GONE);
                            viewLoginHint.setVisibility(View.VISIBLE);

                            refreshCountsPublic();
                            selectStatus(null);
                            return;
                        }

                        // 로그인 성공 → 버튼/행 노출
                        ApiService.UserResponse me = res.body();
                        userId = me.userid; email = me.email; role = me.role;

                        if (rowMine != null) rowMine.setVisibility(View.VISIBLE);
                        btnMineToggle.setVisibility(View.VISIBLE);
                        viewLoginHint.setVisibility(View.GONE);

                        adapter.setPublicMode(!mineOnly);
                        if (mineOnly) refreshCounts(); else refreshCountsPublic();
                        selectStatus(null);
                    }
                    @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                        bearer = null;
                        userId = email = role = null;
                        mineOnly = false;
                        adapter.setPublicMode(true);

                        if (rowMine != null) rowMine.setVisibility(View.GONE);
                        btnMineToggle.setVisibility(View.GONE);
                        viewLoginHint.setVisibility(View.VISIBLE);

                        refreshCountsPublic();
                        selectStatus(null);
                    }
                });
    }

    /** 카운트들 */
    private void refreshCounts() {
        if (bearer == null) { refreshCountsPublic(); return; }
        ApiClient.get(getApplicationContext())
                .getMyInquiries(bearer, userId, email, role, 0, 1, "createdAt,desc", null, null)
                .enqueue(new CountCb(v -> { countAll = v; applyFilterButtonText(); }));
        ApiClient.get(getApplicationContext())
                .getMyInquiries(bearer, userId, email, role, 0, 1, "createdAt,desc", null, "OPEN")
                .enqueue(new CountCb(v -> { countOpen = v; applyFilterButtonText(); }));
        ApiClient.get(getApplicationContext())
                .getMyInquiries(bearer, userId, email, role, 0, 1, "createdAt,desc", null, "ANSWERED")
                .enqueue(new CountCb(v -> { countAnswered = v; applyFilterButtonText(); }));
        ApiClient.get(getApplicationContext())
                .getMyInquiries(bearer, userId, email, role, 0, 1, "createdAt,desc", null, "CLOSED")
                .enqueue(new CountCb(v -> { countClosed = v; applyFilterButtonText(); }));
    }

    private void refreshCountsPublic() {
        ApiClient.get(getApplicationContext())
                .getPublicInquiries(0, 1, "createdAt,desc", null, null)
                .enqueue(new CountCb(v -> { countAll = v; applyFilterButtonText(); }));
        ApiClient.get(getApplicationContext())
                .getPublicInquiries(0, 1, "createdAt,desc", null, "OPEN")
                .enqueue(new CountCb(v -> { countOpen = v; applyFilterButtonText(); }));
        ApiClient.get(getApplicationContext())
                .getPublicInquiries(0, 1, "createdAt,desc", null, "ANSWERED")
                .enqueue(new CountCb(v -> { countAnswered = v; applyFilterButtonText(); }));
        ApiClient.get(getApplicationContext())
                .getPublicInquiries(0, 1, "createdAt,desc", null, "CLOSED")
                .enqueue(new CountCb(v -> { countClosed = v; applyFilterButtonText(); }));
    }

    private interface LongCb { void onResult(long v); }
    private static class CountCb implements Callback<ApiService.PageResponse<ApiService.InquiryResp>> {
        private final LongCb cb; CountCb(LongCb cb){ this.cb = cb; }
        @Override public void onResponse(Call<ApiService.PageResponse<ApiService.InquiryResp>> call,
                                         Response<ApiService.PageResponse<ApiService.InquiryResp>> res) {
            long v = 0; if (res.isSuccessful() && res.body()!=null) v = res.body().totalElements;
            if (cb!=null) cb.onResult(v);
        }
        @Override public void onFailure(Call<ApiService.PageResponse<ApiService.InquiryResp>> call, Throwable t) {
            if (cb!=null) cb.onResult(0);
        }
    }

    private void applyFilterButtonText(){
        btnAll.setText("전체(" + countAll + ")");
        btnOpen.setText("접수(" + countOpen + ")");
        btnAnswered.setText("답변(" + countAnswered + ")");
        btnClosed.setText("종료(" + countClosed + ")");
        btnMineToggle.setText(mineOnly ? "내 문의만: 켬" : "내 문의만: 끔");
    }

    private void selectStatus(@Nullable String st){
        curStatus = st;
        styleButtons();
        reloadFirst();
    }

    private void styleButtons(){
        setBtnStyle(btnAll,      curStatus == null);
        setBtnStyle(btnOpen,     "OPEN".equals(curStatus));
        setBtnStyle(btnAnswered, "ANSWERED".equals(curStatus));
        setBtnStyle(btnClosed,   "CLOSED".equals(curStatus));
        setBtnStyle(btnMineToggle, mineOnly);
    }

    private void setBtnStyle(Button b, boolean on){
        if (on){ b.setBackgroundResource(R.drawable.bg_filter_blue); b.setTextColor(0xFF1A73E8); }
        else   { b.setBackgroundResource(R.drawable.bg_filter_white); b.setTextColor(0xFF222222); }
        b.setBackgroundTintList((ColorStateList) null);
    }

    private void reloadFirst(){ page = 0; last = false; requestPage(true); }
    private void loadNext(){ if (last || loading) return; page++; requestPage(false); }

    private void requestPage(boolean clear){
        if (loading) return;
        loading = true;
        if (clear && !swipe.isRefreshing()) swipe.setRefreshing(true);

        String sort = "createdAt,desc";

        Callback<ApiService.PageResponse<ApiService.InquiryResp>> cb =
                new Callback<ApiService.PageResponse<ApiService.InquiryResp>>() {
                    @Override public void onResponse(Call<ApiService.PageResponse<ApiService.InquiryResp>> call,
                                                     Response<ApiService.PageResponse<ApiService.InquiryResp>> res) {
                        loading = false; swipe.setRefreshing(false);
                        if (!res.isSuccessful() || res.body()==null){
                            if (clear) adapter.setData(Collections.emptyList(), true);
                            Toast.makeText(InquiryActivity.this, "불러오기 실패(" + res.code() + ")", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ApiService.PageResponse<ApiService.InquiryResp> body = res.body();
                        adapter.setData(body.content, clear);
                        last = body.last;

                        long te = body.totalElements;
                        if (curStatus == null) countAll = te;
                        else if ("OPEN".equals(curStatus)) countOpen = te;
                        else if ("ANSWERED".equals(curStatus)) countAnswered = te;
                        else if ("CLOSED".equals(curStatus)) countClosed = te;
                        applyFilterButtonText();
                    }
                    @Override public void onFailure(Call<ApiService.PageResponse<ApiService.InquiryResp>> call, Throwable t) {
                        loading = false; swipe.setRefreshing(false);
                        if (clear) adapter.setData(Collections.emptyList(), true);
                        Toast.makeText(InquiryActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
                    }
                };

        if (mineOnly) {
            adapter.setPublicMode(false);
            ApiClient.get(getApplicationContext())
                    .getMyInquiries(bearer, userId, email, role, page, size, sort, null, curStatus)
                    .enqueue(cb);
        } else {
            adapter.setPublicMode(true);
            ApiClient.get(getApplicationContext())
                    .getPublicInquiries(page, size, sort, null, curStatus)
                    .enqueue(cb);
        }
    }
}
