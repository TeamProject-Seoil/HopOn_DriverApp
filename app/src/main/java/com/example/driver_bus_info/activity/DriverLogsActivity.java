// app/src/main/java/com/example/driver_bus_info/activity/DriverLogsActivity.java
package com.example.driver_bus_info.activity;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.adapter.DriverLogsAdapter;
import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.service.ApiClient;
import com.example.driver_bus_info.service.ApiService;
import com.example.driver_bus_info.util.DeviceInfo;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DriverLogsActivity extends AppCompatActivity {

    private ImageButton ivBack;
    private RecyclerView rv;
    private TextView tvEmpty;

    private final DriverLogsAdapter adapter = new DriverLogsAdapter();
    private ApiService api;

    private TokenManager tm;
    private String clientType;
    private String bearer; // "Bearer xxx"

    private int page = 0;
    private final int size = 20;
    private boolean loading = false;
    private boolean last = false;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_log);

        api = ApiClient.get(getApplicationContext());
        tm = TokenManager.get(this);
        clientType = DeviceInfo.getClientType();
        bearer = ((tm.tokenType() != null ? tm.tokenType() : "Bearer") + " " + tm.accessToken());

        ivBack = findViewById(R.id.ivBack);
        rv     = findViewById(R.id.rvLogs);
        tvEmpty= findViewById(R.id.tvEmpty);

        ivBack.setOnClickListener(v -> finish());

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) r.getLayoutManager();
                if (lm == null) return;
                int total = lm.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();
                if (!loading && !last && lastVisible >= total - 5) {
                    loadPage(false);
                }
            }
        });

        // 첫 페이지
        loadPage(true);
    }

    private void loadPage(boolean first) {
        if (first) { page = 0; last = false; adapter.submit(null, true); }
        loading = true;

        api.getDriverOperations(bearer, clientType, "ENDED", page, size, "endedAt,desc")
                .enqueue(new Callback<ApiService.PageResponse<ApiService.DriverOperationListItem>>() {
                    @Override
                    public void onResponse(Call<ApiService.PageResponse<ApiService.DriverOperationListItem>> call,
                                           Response<ApiService.PageResponse<ApiService.DriverOperationListItem>> res) {
                        loading = false;
                        if (!res.isSuccessful() || res.body() == null) {
                            Toast.makeText(DriverLogsActivity.this, "기록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                            if (first) showEmpty(true);
                            return;
                        }
                        ApiService.PageResponse<ApiService.DriverOperationListItem> p = res.body();
                        List<ApiService.DriverOperationListItem> content = p.content;

                        adapter.submit(content, first);
                        showEmpty(first && (content == null || content.isEmpty()));

                        last = p.last;
                        if (!last) page++;
                    }

                    @Override
                    public void onFailure(Call<ApiService.PageResponse<ApiService.DriverOperationListItem>> call, Throwable t) {
                        loading = false;
                        Toast.makeText(DriverLogsActivity.this, "오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        if (first) showEmpty(true);
                    }
                });
    }

    private void showEmpty(boolean show) {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }
}
