package com.example.driver_bus_info.service;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.util.DeviceInfo;
import android.content.pm.ApplicationInfo;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    // ▶ 네 서버 주소
     private static final String BASE_URL = "http://10.0.2.2:8888/";
    // private static final String BASE_URL = "http://testhopon.p-e.kr:8080/";
    //private static final String BASE_URL = "http://168.138.168.66:8080/";

    private static Retrofit retrofit;
    private static ApiService service;

    private ApiClient() {}

    public static synchronized ApiService get(Context ctx) {
        if (service != null) return service;

        TokenManager tm = TokenManager.get(ctx.getApplicationContext());
        String clientType = DeviceInfo.getClientType();

        // --- 로깅 (릴리즈에서는 민감정보 노출 방지)
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        boolean isDebuggable = (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        logging.setLevel(isDebuggable
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        // --- 공통 헤더 부착: X-Client-Type / Authorization
        Interceptor headerInterceptor = chain -> {
            Request orig = chain.request();
            Request.Builder b = orig.newBuilder()
                    .header("X-Client-Type", clientType);

            String at = tm.accessToken();
            String tt = tm.tokenType();
            // 이미 명시된 Authorization이 없고, 저장된 토큰이 있으면 추가
            if (at != null && orig.header("Authorization") == null) {
                b.header("Authorization", (tt == null ? "Bearer" : tt) + " " + at);
            }
            return chain.proceed(b.build());
        };

        // --- 401 시 자동 refresh (일부 경로 제외)
        Authenticator authenticator = new Authenticator() {
            @Nullable @Override
            public Request authenticate(Route route, Response response) throws IOException {
                // 무한루프 방지 플래그
                if (response.request().header("Authorization-Refresh-Attempt") != null) return null;

                // /auth/* 요청은 리프레시 시도하지 않음
                String path = response.request().url().encodedPath();
                if (path != null && path.startsWith("/auth")) return null;

                String rt = tm.refreshToken();
                String deviceId = tm.deviceId();
                if (rt == null || deviceId == null) return null;

                // refresh 전용 클라이언트 (얇게)
                OkHttpClient bare = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build();

                Retrofit r = new Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .client(bare)
                        .build();

                ApiService refreshSvc = r.create(ApiService.class);
                try {
                    retrofit2.Response<ApiService.AuthResponse> res = refreshSvc
                            .refresh(rt, clientType, deviceId)
                            .execute();

                    if (res.isSuccessful() && res.body() != null) {
                        ApiService.AuthResponse body = res.body();

                        // ▶ access/tokenType 갱신
                        tm.updateAccess(body.accessToken, body.tokenType);
                        // ▶ 서버가 refresh를 회전 발급하면 저장 (TokenManager에 저장 메서드가 있다면 활용)
                        if (body.refreshToken != null) {
                            // role은 기존 저장값 사용
                            tm.saveLogin(body.accessToken, body.refreshToken,
                                    (body.tokenType == null ? "Bearer" : body.tokenType),
                                    tm.role());
                        }

                        // 원 요청 재시도
                        return response.request().newBuilder()
                                .header("Authorization-Refresh-Attempt", "1")
                                .header("Authorization", (body.tokenType == null ? "Bearer" : body.tokenType) + " " + body.accessToken)
                                .header("X-Client-Type", clientType)
                                .build();
                    }
                } catch (Exception ignored) {}
                // 실패 시 포기 -> 원 호출에서 401로 종료
                return null;
            }
        };

        OkHttpClient ok = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS) // 업로드 대비
                .addInterceptor(headerInterceptor)
                .addInterceptor(logging)
                .authenticator(authenticator)
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(ok)
                .build();

        service = retrofit.create(ApiService.class);
        return service;
    }
}
