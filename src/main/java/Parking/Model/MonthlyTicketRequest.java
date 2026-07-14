package Parking.Model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity đại diện cho một Đơn đăng ký mua vé tháng.
 * Khách hàng nộp đơn này trên ứng dụng, sau đó Quản lý (Manager) sẽ duyệt.
 * Duyệt thành công thì hệ thống mới tạo ra một MonthlyTicket (Vé tháng thật).
 */
@Entity
@Getter
@Setter
@Table(name = "monthly_ticket_request")
public class MonthlyTicketRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Mã đơn yêu cầu

    // Đăng ký cho xe nào?
    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"user", "monthlyTickets"})
    private Vehicle vehicle;

    // Ai là người nộp đơn?
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"password", "roles", "staffList", "tokens", "vehicles", "bookings"})
    private User user;

    // Đăng ký gói giá nào? (Ví dụ: Gói xe máy 1 tháng 150k)
    @ManyToOne
    @JoinColumn(name = "policy_id", nullable = false)
    private PricePolicy pricePolicy;

    // Đăng ký gửi ở chi nhánh nào?
    @ManyToOne
    @JoinColumn(name = "branch_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"parkingFloors", "parkingCards", "staffList"})
    private ParkingBranch parkingBranch;

    @Column(nullable = false)
    private Integer status; // Trạng thái đơn: 0 = Đang chờ duyệt (Pending), 1 = Đã duyệt (Approved), 2 = Bị từ chối (Rejected)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(mappedBy = "monthlyTicketRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("monthlyTicketRequest")
    private Payment payment;
}
