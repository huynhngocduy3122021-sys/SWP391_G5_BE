package Parking.Model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.cglib.core.Local;

import Parking.enums.PaymentMethod;
import Parking.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity (Bảng trong Database) đại diện cho một Giao dịch thanh toán.
 */
@Entity
@Getter
@Setter
@Table(
    name = "payments",
    uniqueConstraints = {
        // Ràng buộc: Một Lượt gửi xe chỉ có tối đa 1 Giao dịch thanh toán duy nhất (Không được phép thanh toán 2 lần cho cùng 1 lượt)
        @UniqueConstraint(
            name = "uk_payment_parking_session",
            columnNames = "parking_session_id"
        )
    }
)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId; // Mã thanh toán

    @Column(name = "amount", nullable = false)
    private BigDecimal amount; // Số tiền khách phải trả

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod; // Tiền mặt (CASH) hay VNPay?

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING; // Trạng thái thanh toán (PENDING, PAID, FAILED)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // Thời gian khởi tạo đơn thanh toán
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt; // Thời gian khách thực sự chuyển tiền thành công
    
    // Mã giao dịch nội bộ sinh ra để gửi sang VNPay (Dùng để đối soát)
    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;
    
    // Mã giao dịch thực tế do VNPay trả về khi thanh toán thành công
    @Column(name = "vnp_transaction_no", length = 50)
    private String vnpTransactionNo;
    
    @Column(name = "bank_code", length = 30)
    private String bankCode; // Mã ngân hàng khách dùng để chuyển khoản (VD: VCB, NCB...)
    
    @Column(name = "response_code", length = 10)
    private String responseCode; // Mã lỗi do VNPay trả về (00 là thành công)

    // Thời điểm URL thanh toán của VNPay hết hiệu lực (Khách không chuyển tiền sẽ bị hủy link)
    @Column(name = "payment_expires_at")
    private LocalDateTime paymentExpiresAt;

    // Giao dịch thanh toán này thuộc về Lượt gửi xe (ParkingSession) nào?
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_session_id", unique = true)
    private ParkingSession parkingSession;

    // Hàm này sẽ tự động chạy ngay trước khi dữ liệu được INSERT vào Database lần đầu tiên
    @PrePersist
    public void prePersist(){
        if(createdAt == null) {
            createdAt = LocalDateTime.now(); // Tự động gán giờ hiện tại
        }

        if(paymentStatus == null) {
            paymentStatus = PaymentStatus.PENDING;
        }
    }
}
