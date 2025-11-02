package com.example.driver_bus_info.service;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Retrofit2 API 정의 (통합)
 * - 버스/인증/회원/예약/면허 + 공지/문의 + 드라이버 운행 + 등록 이력 + 운행 기록(페이징)
 */
public interface ApiService {

    // =========================================================
    // ======================= 인증 DTO ========================
    // =========================================================
    class AuthRequest {
        public String userid, password, clientType, deviceId;
        public AuthRequest(String u, String p, String c, String d){
            userid=u; password=p; clientType=c; deviceId=d;
        }
    }
    class AuthResponse { public String accessToken, refreshToken, tokenType, role; }
    class RegisterResponse { public boolean ok; public String message, userid, reason; }
    class CheckResponse { public boolean useridTaken, emailTaken; }

    // =========================================================
    // ======================== 인증 API =======================
    // =========================================================
    @POST("/auth/login")
    Call<AuthResponse> login(@Body AuthRequest body);

    @FormUrlEncoded
    @POST("/auth/refresh")
    Call<AuthResponse> refresh(@Field("refreshToken") String token,
                               @Field("clientType") String clientType,
                               @Field("deviceId") String deviceId);

    @Multipart
    @POST("/auth/register")
    Call<RegisterResponse> register(@Part("data") RequestBody dataJson,
                                    @Part MultipartBody.Part file,
                                    @Part MultipartBody.Part licensePhoto);

    @GET("/auth/check")
    Call<CheckResponse> checkDup(@Query("userid") String userid,
                                 @Query("email") String email);

    // 이메일 인증
    class SendEmailCodeRequest {
        public String email, purpose;
        public SendEmailCodeRequest(String e, String p){ email=e; purpose=p; }
    }
    class VerifyEmailCodeRequest {
        public String verificationId,email,purpose,code;
        public VerifyEmailCodeRequest(String v,String e,String p,String c){
            verificationId=v; email=e; purpose=p; code=c; }
    }

    @POST("/auth/email/send-code")   Call<Map<String,Object>> sendEmail(@Body SendEmailCodeRequest req);
    @POST("/auth/email/verify-code") Call<Map<String,Object>> verifyEmail(@Body VerifyEmailCodeRequest req);

    // 아이디/비번 찾기
    @POST("/auth/find-id-after-verify")        Call<Map<String,Object>> findIdAfterVerify(@Body Map<String,Object> body);
    @POST("/auth/reset-password-after-verify") Call<Map<String,Object>> resetPasswordAfterVerify(@Body Map<String,Object> body);
    @POST("/auth/verify-pw-user")              Call<Map<String,Object>> verifyPwUser(@Body Map<String,Object> body);

    // =========================================================
    // ===================== 로그인 사용자 ======================
    // =========================================================
    @GET("/users/me")
    Call<UserResponse> me(@Header("Authorization") String bearer,
                          @Header("X-Client-Type") String clientType);

    @GET("/users/me/profile-image")
    Call<ResponseBody> meImage(@Header("Authorization") String bearer,
                               @Header("X-Client-Type") String clientType);

    class UserResponse {
        public Long userNum; public String userid, username, email, tel, role;
        public boolean hasProfileImage;
        public String company, approvalStatus;
        public boolean hasDriverLicenseFile;
        @SerializedName(value = "lastLoginAtIso",   alternate = {"lastLoginAt"})   public String lastLoginAtIso;
        @SerializedName(value = "lastRefreshAtIso", alternate = {"lastRefreshAt"}) public String lastRefreshAtIso;
    }

    @Multipart
    @PATCH("/users/me")
    Call<UserResponse> updateMe(@Header("Authorization") String bearer,
                                @Header("X-Client-Type") String clientType,
                                @Part("data") RequestBody dataJson,
                                @Part MultipartBody.Part file);

    class ChangePasswordRequest { public String currentPassword,newPassword;
        public ChangePasswordRequest(String c,String n){ currentPassword=c; newPassword=n; } }

    @POST("/users/me/password")
    Call<Map<String,Object>> changePassword(@Header("Authorization") String bearer,
                                            @Header("X-Client-Type") String clientType,
                                            @Body ChangePasswordRequest body);

    class DeleteAccountRequest { public String currentPassword;
        public DeleteAccountRequest(String c){ currentPassword=c; } }

    @HTTP(method="DELETE",path="/users/me",hasBody=true)
    Call<Map<String,Object>> deleteMe(@Header("Authorization") String bearer,
                                      @Header("X-Client-Type") String clientType,
                                      @Body DeleteAccountRequest body);


    // =========================================================
    // =============== 공통 Page 응답 (공지/문의) ================
    // =========================================================
    class PageResponse<T> {
        public List<T> content;
        @SerializedName(value = "page", alternate = {"number"})
        public int page;
        public int size, totalPages;
        public long totalElements;
        public boolean first, last;
    }

    // =========================================================
    // ===================== 공지(Notice) API ===================
    // =========================================================
    class NoticeResp {
        public Long id;
        public String title, content, noticeType, targetRole;
        public long viewCount;
        public String createdAt, updatedAt;
        @SerializedName(value = "read",   alternate = {"isRead"})   public Boolean read;
        @SerializedName(value = "unread", alternate = {"isUnread"}) public Boolean unread;
        @SerializedName(value = "readAt", alternate = {"read_at"})  public String readAt;
    }

    @GET("/api/notices")
    Call<PageResponse<NoticeResp>> getNotices(@Query("page") int page,
                                              @Query("size") int size,
                                              @Query("sort") String sort,
                                              @Query("q") String q,
                                              @Query("type") String type);

    @GET("/api/notices/{id}")
    Call<NoticeResp> getNoticeDetail(@Header("Authorization") String bearer,
                                     @Path("id") Long id,
                                     @Query("increase") boolean increase);

    @POST("/api/notices/{id}/read")
    Call<Void> markNoticeRead(@Header("Authorization") String bearer,
                              @Path("id") Long id);

    @GET("/api/notices/unread-count")
    Call<Map<String, Integer>> getUnreadNoticeCount(@Header("Authorization") String bearer);

    // =========================================================
    // ===================== 문의(Inquiry) API ==================
    // =========================================================
    class InquiryAtt { public Long id; public String filename, contentType; public long size; }
    class InquiryRep { public Long id; public String message, createdAt; }
    class InquiryResp {
        public Long id;
        public String name, email, userid, title, content, status;
        public boolean secret, hasPassword;
        public List<InquiryAtt> attachments;
        public List<InquiryRep> replies;
        public String createdAt, updatedAt;
    }

    @GET("/api/inquiries/public")
    Call<PageResponse<InquiryResp>> getPublicInquiries(@Query("page") int page,
                                                       @Query("size") int size,
                                                       @Query("sort") String sort,
                                                       @Query("q") String q,
                                                       @Query("status") String status);

    @GET("/api/inquiries")
    Call<PageResponse<InquiryResp>> getMyInquiries(@Header("Authorization") String bearer,
                                                   @Header("X-User-Id") String userId,
                                                   @Header("X-User-Email") String email,
                                                   @Header("X-User-Role") String role,
                                                   @Query("page") int page,
                                                   @Query("size") int size,
                                                   @Query("sort") String sort,
                                                   @Query("q") String q,
                                                   @Query("status") String status);

    @GET("/api/inquiries/{id}/public")
    Call<InquiryResp> getInquiryPublicDetail(@Path("id") Long id,
                                             @Query("password") String password);


    @Multipart
    @POST("/api/inquiries")
    Call<InquiryResp> createInquiry(@Header("X-User-Id") String userId,
                                    @Header("X-User-Email") String email,
                                    @Header("X-User-Role") String role,
                                    @Part("title") RequestBody title,
                                    @Part("content") RequestBody content,
                                    @Part("name") RequestBody name,
                                    @Part("secret") RequestBody secret,
                                    @Part("password") RequestBody password,
                                    @Part List<MultipartBody.Part> files);

    // =========================================================
    // =================== 드라이버 운행 ========================
    // =========================================================

    /** 기사-차량 배정(등록): vehicleId 또는 plateNo 중 하나 */
    class AssignVehicleRequest {
        public String vehicleId;
        public String plateNo;
        public String clientType; // 예: "DRIVER_APP"
        public AssignVehicleRequest(String vehicleId, String plateNo, String clientType) {
            this.vehicleId = vehicleId; this.plateNo = plateNo; this.clientType = clientType;
        }
    }
    class AssignVehicleResponse {
        public String vehicleId, plateNo, routeId, routeName;
    }

    @POST("/api/driver/assign")
    Call<AssignVehicleResponse> assignVehicle(@Header("Authorization") String bearer,
                                              @Header("X-Client-Type") String clientType,
                                              @Body AssignVehicleRequest body);

    /** 운행 시작 요청(기사 현재 위치 포함) */
    class StartOperationRequest {
        public Double lat, lon;
        public String vehicleId; // 선택
        public StartOperationRequest(Double lat, Double lon, String vehicleId) {
            this.lat = lat; this.lon = lon; this.vehicleId = vehicleId;
        }
    }
    class StartOperationResponse {
        public Long operationId;
        public String vehicleId, plateNo;
        public String routeId, routeName;
        public String apiVehId, apiPlainNo;
    }

    @POST("/api/driver/operations/start")
    Call<StartOperationResponse> startOperation(@Header("Authorization") String bearer,
                                                @Header("X-Client-Type") String clientType,
                                                @Body StartOperationRequest body);

    /** 하트비트(운행 중 위치 업데이트) */
    class HeartbeatRequest {
        public Double lat, lon;
        public HeartbeatRequest(Double lat, Double lon){ this.lat=lat; this.lon=lon; }
    }

    @POST("/api/driver/operations/heartbeat")
    Call<Map<String,Object>> heartbeat(@Header("Authorization") String bearer,
                                       @Header("X-Client-Type") String clientType,
                                       @Body HeartbeatRequest body);

    /** 운행 종료 */
    class EndOperationRequest { public String memo;
        public EndOperationRequest(String memo){ this.memo=memo; } }

    @POST("/api/driver/operations/end")
    Call<Map<String,Object>> endOperation(@Header("Authorization") String bearer,
                                          @Header("X-Client-Type") String clientType,
                                          @Body EndOperationRequest body);

    /** 현재 운행 조회(없으면 null 반환) */
    class ActiveOperationResp {
        public Long id;
        public Long userNum;
        public String vehicleId;
        public String routeId, routeName;
        public String apiVehId, apiPlainNo;
        public String status;           // RUNNING | ENDED
        public String startedAt, endedAt;
        public Double lastLat, lastLon;
        public String updatedAt;
    }

    @GET("/api/driver/operations/active")
    Call<ActiveOperationResp> getActiveOperation(@Header("Authorization") String bearer,
                                                 @Header("X-Client-Type") String clientType);

    // 실시간 이동용: 위치 폴링 엔드포인트
    class DriverLocationDto {
        public Long operationId;
        public Double lat, lon;
        public String updatedAtIso;
        public boolean stale;
    }

    // =========================================================
    // =============== 드라이버 등록 이력(최근 선택) =============
    // =========================================================
    class DriverVehicleRegistrationDto {
        public String vehicleId;
        public String plateNo;
        public String routeId;
        public String routeName;
        @SerializedName(value = "createdAtIso", alternate = {"createdAt"})
        public String createdAt;
        // 백엔드 메타(노선유형)
        public Integer routeTypeCode;
        public String  routeTypeLabel;
    }

    /** 내 등록 이력 조회 (최근순) */
    @GET("/api/driver/registrations")
    Call<List<DriverVehicleRegistrationDto>> getDriverRegistrations(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType
    );

    /** 이력에서 제거 */
    @DELETE("/api/driver/registrations/{vehicleId}")
    Call<Void> deleteDriverRegistration(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType,
            @Path("vehicleId") String vehicleId
    );

    // =========================================================
    // =========== 드라이버 운행 “이번/다음 정류장” ===============
    // =========================================================
    class ArrivalNowResponse {
        public String currentStopId;   // 서버가 주면 사용
        public String currentStopName;
        public String nextStopId;      // 서버가 주면 사용
        public String nextStopName;
        public Integer etaSec;
        public Integer routeTypeCode;
        public String  routeTypeLabel;
    }

    @GET("/api/driver/operations/arrival-now")
    Call<ArrivalNowResponse> arrivalNow(@Header("Authorization") String bearer,
                                        @Header("X-Client-Type") String clientType);

    // =========================================================
    // ================ 운행 기록(페이징/엔디드) =================
    // =========================================================

    class DriverOperationListItem {
        public Long id;
        public String routeId, routeName;
        public String vehicleId, plateNo;
        public String startedAt, endedAt;    // ISO 문자열
        public Integer routeTypeCode;        // 선택
        public String  routeTypeLabel;       // 선택
    }

    @GET("/api/driver/operations")
    Call<PageResponse<DriverOperationListItem>> getDriverOperations(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType,
            @Query("status") String status,         // "ENDED" | "RUNNING" | null(전체)
            @Query("page")   int page,
            @Query("size")   int size,
            @Query("sort")   String sort
    );

    class DriverOperationResp {
        public Long id;
        public Long userNum;
        public String vehicleId;
        public String routeId, routeName;
        public String apiVehId, apiPlainNo;
        public String status;         // RUNNING | ENDED
        public String startedAt, endedAt;
        public Double lastLat, lastLon;
        public String updatedAt;
        public Integer routeTypeCode; // 선택
        public String  routeTypeLabel;// 선택
    }

    // ===== 승객 현황 =====
    /** ★ 경로 수정: /api/driver/passengers/now */
    @GET("/api/driver/passengers/now")
    Call<ApiService.DriverPassengerListResponse> getDriverPassengers(
            @Header("Authorization") String bearer,
            @Header("X-Client-Type") String clientType
    );

    // ===== DTOs =====
    class DriverPassengerListResponse {
        public Long   operationId;
        public String routeId;
        public String routeName;
        public Integer count;
        public List<DriverPassengerDto> items;
    }
    class DriverPassengerDto {
        public Long   reservationId;
        public Long   userNum;
        public String username;
        public String userid;             // 서버에서 userid로 내려온다고 가정
        public String boardingStopId;
        public String boardingStopName;
        public String alightingStopId;
        public String alightingStopName;
        public String status;
        public String createdAtIso;
        public String updatedAtIso;
    }
}
