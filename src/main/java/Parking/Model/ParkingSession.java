package Parking.Model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import Parking.enums.ParkingSessionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

/**
 * Entity (Bảng trong Database) đại diện cho một Lượt gửi xe (Phiên gửi xe).
 * Một ParkingSession bắt đầu khi xe quẹt thẻ vào bãi và kết thúc khi xe thanh toán và ra khỏi bãi.
 */
@Entity
@Getter
@Setter
@Table(
    name = "parking_session",
    // Tạo index để tăng tốc độ tìm kiếm khi nhân viên quét thẻ hoặc biển số xe
    indexes = {
        @Index(
            name = "idx_session_vehicle_status",
            columnList = "vehicle_id, status"
        ),
        @Index(
            name = "idx_session_card_status",
            columnList = "parking_card_id, status"
        )
    }
)
public class ParkingSession {
    
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "parking_session_id")
        private Long parkingSessionId; // Mã phiên gửi xe

        @Column(name = "check_in_time", nullable = false)
        private LocalDateTime checkInTime; // Thời gian xe chạy vào bãi

        @Column(name = "check_out_time")
        private LocalDateTime checkOutTime; // Thời gian xe rời khỏi bãi

        @Column(name = "total_amount")
        private BigDecimal totalAmount; // Tổng tiền khách phải trả (Đã bao gồm tiền phạt nếu có)

        @Column(name = "penalty_fee")
        private BigDecimal penaltyFee; // Tiền phạt (Ví dụ: Làm mất thẻ, gửi quá giờ...)

        @Column(name = "parking_fee")
        private BigDecimal parkingFee; // Tiền gửi xe gốc (Dựa theo bảng giá)

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false)
        private ParkingSessionStatus status = ParkingSessionStatus.ACTIVE; // Trạng thái: ACTIVE (Đang gửi) hoặc COMPLETED (Đã lấy xe)

        // Mối quan hệ: Một phiên gửi xe gắn với một Chiếc xe cụ thể
        @ManyToOne
        @JoinColumn(name = "vehicle_id", nullable = false)
        private Vehicle vehicle;

        // Mối quan hệ: Khách đang cầm Thẻ vật lý nào để gửi xe?
        @ManyToOne
        @JoinColumn(name = "parking_card_id", nullable = false)
        private ParkingCard parkingCard;



        // Mối quan hệ 1-1 với hóa đơn Thanh toán. Một lượt gửi xe sẽ có 1 giao dịch thanh toán khi ra về
        @OneToOne(mappedBy = "parkingSession", cascade = CascadeType.ALL)
        private Payment payment;
        
        // Chi nhánh mà xe này đang đậu
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "parking_branch_id", nullable = false)
        private ParkingBranch parkingBranch;

        // Hình ảnh chụp lại xe (thường là 2 ảnh: Chụp biển số trước và mặt người lái lúc check-in)
        @OneToMany( mappedBy = "parkingSession",fetch = FetchType.LAZY)
        private List<VehicleImage> vehicleImages = new ArrayList<>();

}
