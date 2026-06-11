package Parking.Model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import Parking.enums.ParkingSessionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Table;

@Entity
@Getter
@Setter
@Table(name = "parking_session")
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

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false)
        private ParkingSessionStatus status = ParkingSessionStatus.ACTIVE;

        @ManyToOne
        @JoinColumn(name = "vehicle_id", nullable = false)
        private Vehicle vehicle;

        @ManyToOne
        @JoinColumn(name = "parking_card_id", nullable = false)
        private ParkingCard parkingCard;

        @ManyToOne
        @JoinColumn(name = "parking_zone_id", nullable = false)
        private ParkingZone parkingZone;

        @OneToOne(mappedBy = "parkingSession", cascade = CascadeType.ALL)
        private Payment payment;
}
