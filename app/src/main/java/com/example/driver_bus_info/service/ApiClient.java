package com.example.driver_bus_info.service;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.annotation.Nullable;

import com.example.driver_bus_info.core.TokenManager;
import com.example.driver_bus_info.util.DeviceInfo;

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

    // 로컬 에뮬레이터 -> PC Spring 서버
    //private static final String BASE_URL = "http://10.0.2.2:8080/";
    //private static final String BASE_URL = "http://testhopon.p-e.kr:8080/";
    //private static final String BASE_URL = "http://168.138.168.66:8080/";
    private static final String BASE_URL = "http://52.78.245.249:8080/";
    private static Retrofit retrofit;
    private static ApiService service;

    private ApiClient() {}

    /** 항상 ApplicationContext로 호출하세요. */
    public static synchronized ApiService get(Context ctx) {
        if (service != null) return service;

        final Context app = ctx.getApplicationContext();
        final TokenManager tm = TokenManager.get(app);
        final String clientType = DeviceInfo.getClientType();

        // Logging
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        boolean isDebuggable = (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        logging.setLevel(isDebuggable ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);

        // 공통 헤더 인터셉터
        Interceptor headerInterceptor = chain -> {
            Request orig = chain.request();
            Request.Builder b = orig.newBuilder()
                    .header("X-Client-Type", clientType);

            String at = tm.accessToken();
            String tt = tm.tokenType();
            if (at != null && orig.header("Authorization") == null) {
                b.header("Authorization", (tt == null ? "Bearer" : tt) + " " + at);
            }
            return chain.proceed(b.build());
        };

        // 401 대응: 자동 refresh
        Authenticator authenticator = new Authenticator() {
            @Nullable @Override
            public Request authenticate(Route route, Response response) throws IOException {
                if (response.request().header("Authorization-Refresh-Attempt") != null) return null;

                String path = response.request().url().encodedPath();
                if (path != null && path.startsWith("/auth")) return null;

                String rt = tm.refreshToken();
                String deviceId = tm.deviceId();
                if (rt == null || deviceId == null) return null;

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
                    retrofit2.Response<ApiService.AuthResponse> res =
                            refreshSvc.refresh(rt, clientType, deviceId).execute();

                    if (res.isSuccessful() && res.body() != null) {
                        ApiService.AuthResponse body = res.body();

                        tm.updateAccess(body.accessToken, body.tokenType);
                        if (body.refreshToken != null) {
                            tm.saveLogin(body.accessToken, body.refreshToken,
                                    (body.tokenType == null ? "Bearer" : body.tokenType),
                                    tm.role());
                        }

                        return response.request().newBuilder()
                                .header("Authorization-Refresh-Attempt", "1")
                                .header("Authorization", (body.tokenType == null ? "Bearer" : body.tokenType) + " " + body.accessToken)
                                .header("X-Client-Type", clientType)
                                .build();
                    }
                } catch (Exception ignored) {}
                return null;
            }
        };

        OkHttpClient ok = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
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
