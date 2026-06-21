package Parking.Model;

import org.hibernate.annotations.ManyToAny;

import Parking.enums.ParkingCardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(
    name = "parking_card",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_parking_card_code",
            columnNames = "card_code"
        )
    }
)
public class ParkingCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parking_card_id")
    private Long parkingCardId;

    @Column(name = "card_code", nullable = false,unique = true)
    private String cardCode;

    @Column(name = "status")
    private ParkingCardStatus status = ParkingCardStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY , optional = false)
    @JoinColumn(name = "parking_branch_id")
    private ParkingBranch parkingBranch;


}
