package Parking.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Lớp DTO (Data Transfer Object) dùng để trả về thông tin chi tiết của một vé xe tháng (Monthly Ticket).
 * Nó được sử dụng để định dạng và chứa dữ liệu gửi từ Backend về cho Frontend.
 * 
 * Các annotation của thư viện Lombok:
 * - @Getter, @Setter: Tự động sinh ra các phương thức get/set cho các thuộc tính.
 * - @Builder: Giúp khởi tạo đối tượng dễ dàng và linh hoạt hơn (ví dụ: MonthlyTicketResponse.builder().ticketId(1L).build()).
 */
@Getter
@Setter
@Builder
public class MonthlyTicketResponse {

    @Getter
    @Setter
    @Builder
    public static class PricePolicySummary {
        private Long pricePolicyId;
        private String policyName;
        private Long vehicleTypeId;
        private String vehicleTypeName;
    }

    // Mã định danh duy nhất của vé tháng trong cơ sở dữ liệu
    private Long ticketId;

    // Mã định danh của xe được đăng ký tương ứng với vé này
    private Long vehicleId;

    private Long vehicleTypeId;

    // Biển số xe của chiếc xe đã đăng ký vé
    private String licensePlate;

    // Mã định danh của thẻ giữ xe (thẻ từ/thẻ cứng) liên kết với vé này
    private Long parkingCardId;

    // Mã in trên thẻ giữ xe hoặc mã định danh của thẻ
    private String cardCode;

    // Tên của khách hàng sở hữu/đăng ký vé tháng
    private String guestName;

    // Số điện thoại liên lạc của khách hàng
    private String guestPhone;

    // Thời điểm (ngày giờ) bắt đầu có hiệu lực của vé tháng
    private LocalDateTime startDate;

    // Thời điểm (ngày giờ) hết hạn của vé tháng
    private LocalDateTime endDate;

    // Mã định danh của chi nhánh bãi đỗ xe nơi vé này được đăng ký
    private Long parkingBranchId;

    // Tên của chi nhánh bãi đỗ xe
    private String parkingBranchName;

    private Long pricePolicyId;

    private PricePolicySummary pricePolicy;

    private Long monthlyTicketRequestId;

    private Integer status; // 1 = Active, 0 = Expired/Locked

    private LocalDateTime createdAt;
}
