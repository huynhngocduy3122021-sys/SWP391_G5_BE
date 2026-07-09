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

@Entity
@Getter
@Setter
@Table(
    name = "parking_session",
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
        private Long parkingSessionId;

        @Column(name = "check_in_time", nullable = false)
        private LocalDateTime checkInTime;

        @Column(name = "check_out_time")
        private LocalDateTime checkOutTime;

        @Column(name = "total_amount")
        private BigDecimal totalAmount;

        @Column(name = "penalty_fee")
        private BigDecimal penaltyFee;

        @Column(name = "parking_fee")
        private BigDecimal parkingFee;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false)
        private ParkingSessionStatus status = ParkingSessionStatus.ACTIVE;

        @ManyToOne
        @JoinColumn(name = "vehicle_id", nullable = false)
        private Vehicle vehicle;

        @ManyToOne
        @JoinColumn(name = "parking_card_id", nullable = false)
        private ParkingCard parkingCard;



        @OneToOne(mappedBy = "parkingSession", cascade = CascadeType.ALL)
        private Payment payment;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "parking_branch_id", nullable = false)
        private ParkingBranch parkingBranch;

        @OneToMany( mappedBy = "parkingSession",fetch = FetchType.LAZY)
        private List<VehicleImage> vehicleImages = new ArrayList<>();


}
