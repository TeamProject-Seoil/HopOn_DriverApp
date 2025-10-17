package com.example.driver_bus_info.service;

import com.example.driver_bus_info.dto.ArrivalDto;
import com.example.driver_bus_info.dto.BusLocationDto;
import com.example.driver_bus_info.dto.BusRouteDto;
import com.example.driver_bus_info.dto.ReservationCreateRequest;
import com.example.driver_bus_info.dto.ReservationResponse;
import com.example.driver_bus_info.dto.StationDto;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    // ====== 버스 관련 ======
    @GET("/api/nearstations")
    Call<List<StationDto>> getNearStations(
            @Query("x") double x,
            @Query("y") double y,
            @Query("radius") int radius
    );

    @GET("/api/stationStop")
    Call<List<ArrivalDto>> getStationArrivals(@Query("arsId") String arsId);

    @GET("/api/busLocation")
    Call<List<BusLocationDto>> getBusLocation(@Query("busRouteId") String busRouteId);

    @GET("/api/busStopList")
    Call<List<BusRouteDto>> getBusRoute(@Query("busRouteId") String busRouteId);

    // ====== 인증 DTO ======
    class AuthRequest {
        public String userid;
        public String password;
        /** USER_APP | DRIVER_APP | ADMIN_APP */
        public String clientType;
        public String deviceId;

        public AuthRequest(String u, String p, String c, String d) {
            userid = u; password = p; clientType = c; deviceId = d;
        }
    }

    class AuthResponse {
        public String accessToken;
        public String refreshToken;
        public String tokenType;
        public String role;
    }

    class RegisterResponse {
        public boolean ok;
        public String message;
        public String userid;
        public String reason;
    }

    class CheckResponse {
        public boolean useridTaken;
        public boolean emailTaken;
    }

    // ====== 인증 API ======
    @POST("/auth/login")
    Call<AuthResponse> login(@Body AuthRequest body);

    @FormUrlEncoded
    @POST("/auth/refresh")
    Call<AuthResponse> refresh(
            @Field("refreshToken") String refreshToken,
            @Field("clientType") String clientType,
            @Field("deviceId") String deviceId
    );

    // 회원가입: data(JSON), file(프로필), licensePhoto(드라이버일 때 필수)
    @Multipart
    @POST("/auth/register")
    Call<RegisterResponse> register(
            @Part("data") RequestBody dataJson,
            @Part MultipartBody.Part file,
            @Part MultipartBody.Part licensePhoto
    );

    @GET("/auth/check")
    Call<CheckResponse> checkDup(
            @Query("userid") String userid,
            @Query("email") String email
    );

    // ====== 이메일 인증 ======
    @POST("/auth/email/send-code")
    Call<Map<String, Object>> sendEmail(@Body SendEmailCodeRequest req);

    @POST("/auth/email/verify-code")
    Call<Map<String, Object>> verifyEmail(@Body VerifyEmailCodeRequest req);

    class SendEmailCodeRequest {
        public String email;
        /** REGISTER | FIND_ID | FIND_PW */
        public String purpose;

        public SendEmailCodeRequest(String email, String purpose) {
            this.email = email; this.purpose = purpose;
        }
    }

    class VerifyEmailCodeRequest {
        public String verificationId;
        public String email;
        /** REGISTER | FIND_ID | FIND_PW */
        public String purpose;
        public String code;

        public VerifyEmailCodeRequest(String verificationId, String email, String purpose, String code) {
            this.verificationId = verificationId; this.email = email; this.purpose = purpose; this.code = code;
        }
    }

    // ====== 아이디/비번 찾기 ======
    @POST("/auth/find-id-after-verify")
    Call<Map<String, Object>> findIdAfterVerify(@Body Map<String, Object> body);

    @POST("/auth/reset-password-after-verify")
    Call<Map<String, Object>> resetPasswordAfterVerify(@Body Map<String, Object> body);

    @POST("/auth/verify-pw-user")
    Call<Map<String, Object>> verifyPwUser(@Body Map<String, Object> body);

    // ====== 로그인 사용자 ======
    @GET("/users/me")
    Call<UserResponse> me(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType
    );

    @GET("/users/me/profile-image")
    Call<ResponseBody> meImage(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType
    );

    @POST("/auth/logout")
    Call<Void> logout(@Body LogoutRequest body);

    public static class UserResponse {
        public Long userNum;
        public String userid;
        public String username;
        public String email;
        public String tel;
        public String role;
        public boolean hasProfileImage;

        // 서버(UserResponse)와 맞춤
        public String company;
        /** APPROVED | PENDING | REJECTED */
        public String approvalStatus;
        /** 서버에서 '면허 존재 여부'로 매핑 */
        public boolean hasDriverLicenseFile;

        // 서버가 내려주는 최근 로그인/활동 시각 (ISO8601 with offset)
        @SerializedName(value = "lastLoginAtIso", alternate = {"lastLoginAt"})
        public String lastLoginAtIso;

        @SerializedName(value = "lastRefreshAtIso", alternate = {"lastRefreshAt"})
        public String lastRefreshAtIso;
    }

    class LogoutRequest {
        public String clientType;
        public String deviceId;
        public String refreshToken;

        public LogoutRequest(String clientType, String deviceId, String refreshToken) {
            this.clientType = clientType; this.deviceId = deviceId; this.refreshToken = refreshToken;
        }
    }

    // ====== 개인정보 수정 ======
    @Multipart
    @PATCH("/users/me")
    Call<UserResponse> updateMe(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType,
            @Part("data") RequestBody dataJson,
            @Part MultipartBody.Part file
    );

    // ====== 비밀번호 변경 ======
    @POST("/users/me/password")
    Call<ResponseBody> changePassword(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType,
            @Body ChangePasswordRequest body
    );

    class ChangePasswordRequest {
        public String currentPassword;
        public String newPassword;

        public ChangePasswordRequest(String c, String n) {
            this.currentPassword = c; this.newPassword = n;
        }
    }

    // ====== 회원 탈퇴 ======
    @HTTP(method = "DELETE", path = "/users/me", hasBody = true)
    Call<Map<String, Object>> deleteMe(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType,
            @Body DeleteAccountRequest body
    );

    class DeleteAccountRequest {
        public String currentPassword;

        public DeleteAccountRequest(String currentPassword) { this.currentPassword = currentPassword; }
    }

    // ====== 예약 ======
    @POST("/api/reservations")
    Call<ReservationResponse> createReservation(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType,
            @Body ReservationCreateRequest body
    );

    // ====== 운전면허(드라이버) ======
    /** 내 면허 요약 조회 */
    @GET("/users/me/driver-license")
    Call<Map<String, Object>> getMyDriverLicense(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType
    );

    /** 내 면허 이미지 */
    @GET("/users/me/driver-license/image")
    Call<ResponseBody> getMyDriverLicenseImage(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType
    );

    /** 면허 업서트 */
    @Multipart
    @POST("/users/me/driver-license")
    Call<Map<String, Object>> upsertMyDriverLicense(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType,
            @Part("licenseNumber") RequestBody licenseNumber,
            @Part("acquiredDate") RequestBody acquiredDate,
            @Part("birthDate")    RequestBody birthDate,
            @Part("name")         RequestBody name,
            @Part MultipartBody.Part photo
    );

    /** 면허 삭제 */
    @DELETE("/users/me/driver-license")
    Call<Map<String, Object>> deleteMyDriverLicense(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType
    );
}
