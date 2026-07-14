package Parking.Model;

import java.time.LocalDateTime;

import Parking.enums.BookingStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity đại diện cho một Lượt đặt chỗ trước (Booking) qua App.
 * Khách hàng có thể đặt chỗ đỗ xe trước khi đến bãi.
 */
@Entity
@Getter
@Setter
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId; // Mã đặt chỗ

    @Column(name = "booking_code", nullable = false, unique = true, length = 20)
    private String bookingCode; // Mã code (Ví dụ: BK-123456) dùng để tạo QR Code cho khách quét

    // Khách hàng nào đặt chỗ?
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Đặt chỗ ở chi nhánh nào?
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parking_branch_id", nullable = false)
    private ParkingBranch parkingBranch;

    // Đặt cho chiếc xe nào?
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType;

    // Sau khi khách hàng đến và quẹt thẻ, Booking này sẽ được liên kết với một Phiên đỗ xe (ParkingSession) thực tế
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_session_id", nullable = true)
    private ParkingSession parkingSession;

    @Column(name = "expected_arrival_time", nullable = false)
    private LocalDateTime expectedArrivalTime; // Thời gian dự kiến khách sẽ tới bãi

    @Column(name = "hold_until", nullable = false)
    private LocalDateTime holdUntil; // Bãi đỗ sẽ giữ chỗ cho khách đến tối đa mấy giờ? (Ví dụ: Quá 30 phút sẽ tự động hủy)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED; // Trạng thái đặt chỗ (PENDING, CONFIRMED, COMPLETED, CANCELLED)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(); // Thời gian tạo booking

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now(); // Thời gian cập nhật gần nhất

    @Column(name = "cancelled_at", nullable = true)
    private LocalDateTime cancelledAt; // Nếu bị hủy thì lưu giờ hủy

    @Column(name = "completed_at", nullable = true)
    private LocalDateTime completedAt; // Nếu hoàn thành thì lưu giờ hoàn thành

    @Column(name = "expired_at", nullable = true)
    private LocalDateTime expiredAt; // Nếu quá hạn (holdUntil) mà khách không tới thì lưu giờ hết hạn
}
