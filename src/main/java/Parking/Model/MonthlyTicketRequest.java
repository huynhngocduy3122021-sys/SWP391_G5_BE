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
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"password", "roles", "staffList", "tokens", "vehicles", "bookings"})
    private User user;

    @ManyToOne
    @JoinColumn(name = "policy_id", nullable = false)
    private PricePolicy pricePolicy;

    @ManyToOne
    @JoinColumn(name = "branch_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"parkingFloors", "parkingCards", "staffList"})
    private ParkingBranch parkingBranch;

    @Column(nullable = false)
    private Integer status; // 0 = Pending, 1 = Approved, 2 = Rejected

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
