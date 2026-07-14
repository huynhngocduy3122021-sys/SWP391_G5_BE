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

@Entity
@Getter
@Setter
@Table(
    name = "payments",
    uniqueConstraints = {
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
    private Long paymentId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    //MÃ gửi qua sang VNPay 
    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;
    // mã VnPay trả về khi thanh toán 
    @Column(name = "vnp_transaction_no", length = 50)
    private String vnpTransactionNo;
    @Column(name = "bank_code", length = 30)
    private String bankCode;
    @Column(name = "response_code", length = 10)
    private String responseCode;

    // thời điểm URK thanh toán hết hiệu lực
    @Column(name = "payment_expires_at")
    private LocalDateTime paymentExpiresAt;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_session_id", unique = true)
    private ParkingSession parkingSession;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_ticket_request_id", unique = true)
    private MonthlyTicketRequest monthlyTicketRequest;

    @PrePersist
    public void prePersist(){
        if(createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if(paymentStatus == null) {
            paymentStatus = PaymentStatus.PENDING;
        }
    }
}
