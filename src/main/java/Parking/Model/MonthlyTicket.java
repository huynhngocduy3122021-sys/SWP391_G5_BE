package Parking.Model;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "monthly_ticket")
public class MonthlyTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "parking_card_id", nullable = false)
    private ParkingCard parkingCard;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "price_policy_id")
    private PricePolicy pricePolicy;

    /**
     * Yêu cầu đã trực tiếp phát hành vé này.
     *
     * Không suy ra quan hệ phát hành bằng vehicleId vì một xe có thể có nhiều
     * lần mua/gia hạn khác nhau.
     */
    @OneToOne
    @JoinColumn(name = "monthly_ticket_request_id", unique = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private MonthlyTicketRequest monthlyTicketRequest;


    @Column(name = "guest_name", columnDefinition = "NVARCHAR(255)")
    private String guestName;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "status", nullable = false)
    @jakarta.persistence.Convert(converter = Parking.enums.MonthlyTicketStatusConverter.class)
    private Parking.enums.MonthlyTicketStatus status; // 1 = Active, 0 = Expired/Locked

    @Column(name = "created_at", nullable = true, updatable = false)
    private LocalDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
