package Parking.Model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "monthly_ticket_request")
public class MonthlyTicketRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"user", "monthlyTickets"})
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"userPassword", "password", "roles", "staffList", "tokens", "vehicles", "bookings", "authorities", "accountNonExpired", "accountNonLocked", "credentialsNonExpired", "enabled", "username"})
    private User user;

    @ManyToOne
    @JoinColumn(name = "policy_id", nullable = false)
    private PricePolicy pricePolicy;

    @ManyToOne
    @JoinColumn(name = "branch_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"parkingFloors", "parkingCards", "staffList"})
    private ParkingBranch parkingBranch;

    @Column(nullable = false)
    @jakarta.persistence.Convert(converter = Parking.enums.MonthlyTicketRequestStatusConverter.class)
    private Parking.enums.MonthlyTicketRequestStatus status; // 0 = PENDING_PAYMENT, 1 = PENDING_APPROVAL, 2 = APPROVED, -1 = REJECTED, -2 = EXPIRED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(mappedBy = "monthlyTicketRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("monthlyTicketRequest")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renewal_of_ticket_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"vehicle", "parkingCard"})
    private MonthlyTicket renewalOfTicket;
}
