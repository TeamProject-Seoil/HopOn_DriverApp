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

/**
 * Retrofit2 API 정의 (통합판)
 * - 버스/인증/회원/예약/면허 + 공지/문의
 * - 공통 헤더(X-Client-Type, Authorization 기본) 는 ApiClient 인터셉터에서 자동 부착 가능하지만,
 *   현재 액티비티 코드에서 clientType을 개별 전달하므로, 필요한 엔드포인트에 명시적으로 파라미터를 둔다.
 */
public interface ApiService {

    // =========================================================
    // ================= 버스 / 정류장 ==========================
    // =========================================================
    @GET("/api/nearstations")
    Call<List<StationDto>> getNearStations(@Query("x") double x,
                                           @Query("y") double y,
                                           @Query("radius") int radius);

    @GET("/api/stationStop")
    Call<List<ArrivalDto>> getStationArrivals(@Query("arsId") String arsId);

    @GET("/api/busLocation")
    Call<List<BusLocationDto>> getBusLocation(@Query("busRouteId") String busRouteId);

    @GET("/api/busStopList")
    Call<List<BusRouteDto>> getBusRoute(@Query("busRouteId") String busRouteId);

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

    // 회원가입: data(JSON), file(프로필), licensePhoto(드라이버일 때 필수)
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

    @POST("/auth/logout")
    Call<Void> logout(@Body LogoutRequest body);

    class UserResponse {
        public Long userNum; public String userid, username, email, tel, role;
        public boolean hasProfileImage;
        public String company, approvalStatus;
        public boolean hasDriverLicenseFile;
        @SerializedName(value = "lastLoginAtIso",  alternate = {"lastLoginAt"})  public String lastLoginAtIso;
        @SerializedName(value = "lastRefreshAtIso", alternate = {"lastRefreshAt"}) public String lastRefreshAtIso;
    }
    class LogoutRequest {
        public String clientType, deviceId, refreshToken;
        public LogoutRequest(String c,String d,String r){ clientType=c; deviceId=d; refreshToken=r; }
    }

    // 개인정보 수정 / 비밀번호 변경 / 탈퇴
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
    // ========================= 예약 ===========================
    // =========================================================
    @POST("/api/reservations")
    Call<ReservationResponse> createReservation(@Header("Authorization") String bearer,
                                                @Body ReservationCreateRequest body);

    // =========================================================
    // =================== 운전면허(드라이버) ====================
    // =========================================================
    @GET("/users/me/driver-license")
    Call<Map<String, Object>> getMyDriverLicense(@Header("Authorization") String bearer);

    @GET("/users/me/driver-license/image")
    Call<ResponseBody> getMyDriverLicenseImage(@Header("Authorization") String bearer);

    @Multipart
    @POST("/users/me/driver-license")
    Call<Map<String, Object>> upsertMyDriverLicense(@Header("Authorization") String bearer,
                                                    @Part("licenseNumber") RequestBody licenseNumber,
                                                    @Part("acquiredDate") RequestBody acquiredDate,
                                                    @Part("birthDate")    RequestBody birthDate,
                                                    @Part("name")         RequestBody name,
                                                    @Part MultipartBody.Part photo);

    @DELETE("/users/me/driver-license")
    Call<Map<String, Object>> deleteMyDriverLicense(@Header("Authorization") String bearer);

    // =========================================================
    // =============== 공통 Page 응답 (공지/문의에 사용) =========
    // =========================================================
    class PageResponse<T> {
        public List<T> content;
        public int number, size, totalPages;
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
    }

    /**
     * 공지 목록
     * (필요 시 인터셉터로 Authorization 자동 부착. 여기서는 파라미터 없이 사용)
     */
    @GET("/api/notices")
    Call<PageResponse<NoticeResp>> getNotices(@Query("page") int page,
                                              @Query("size") int size,
                                              @Query("sort") String sort,
                                              @Query("q") String q,
                                              @Query("type") String type);

    /** 공지 상세 (increase=true면 조회수 +1, markRead=true면 사용자 기준 읽음 처리) */
    @GET("/api/notices/{id}")
    Call<NoticeResp> getNoticeDetail(@Header("Authorization") String bearer,
                                     @Path("id") Long id,
                                     @Query("increase") boolean increase);

    // ★ 전용 읽음 처리 호출
    @POST("/api/notices/{id}/read")
    Call<Void> markNoticeRead(@Header("Authorization") String bearer,
                              @Path("id") Long id);

    /** 미확인 공지 개수 (배지용) */
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

    /** 공개 목록(비로그인 가능) */
    @GET("/api/inquiries/public")
    Call<PageResponse<InquiryResp>> getPublicInquiries(@Query("page") int page,
                                                       @Query("size") int size,
                                                       @Query("sort") String sort,
                                                       @Query("q") String q,
                                                       @Query("status") String status);

    /** 내 문의 목록(로그인 필요) */
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

    /** 내 문의 상세(로그인 필요) */
    @GET("/api/inquiries/{id}")
    Call<InquiryResp> getMyInquiryDetail(@Header("Authorization") String bearer,
                                         @Header("X-User-Id") String userId,
                                         @Header("X-User-Email") String email,
                                         @Header("X-User-Role") String role,
                                         @Path("id") Long id);

    /** 공개 상세(비밀번호 필요 시 password 포함) */
    @GET("/api/inquiries/{id}/public")
    Call<InquiryResp> getInquiryPublicDetail(@Path("id") Long id,
                                             @Query("password") String password);

    /** 첨부 다운로드(내 문의) */
    @GET("/api/inquiries/{inquiryId}/attachments/{attId}")
    Call<ResponseBody> downloadInquiryAttachment(@Header("Authorization") String bearer,
                                                 @Header("X-User-Id") String userId,
                                                 @Header("X-User-Email") String email,
                                                 @Header("X-User-Role") String role,
                                                 @Path("inquiryId") Long inquiryId,
                                                 @Path("attId") Long attId,
                                                 @Query("inline") boolean inline);

    /** 첨부 다운로드(공개) */
    @GET("/api/inquiries/{inquiryId}/attachments/{attId}/public")
    Call<ResponseBody> downloadInquiryAttachmentPublic(@Path("inquiryId") Long inquiryId,
                                                       @Path("attId") Long attId,
                                                       @Query("password") String password,
                                                       @Query("inline") boolean inline);

    /** 문의 작성(multipart, 로그인/비로그인 공용) */
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
}
