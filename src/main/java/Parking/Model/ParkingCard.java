package Parking.Model;

import Parking.enums.ParkingCardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "parking_card")
public class ParkingCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parking_card_id")
    private Long parkingCardId;

    @Column(name = "card_code", nullable = false,unique = true)
    private String cardCode;

    @Column(name = "status")
    private ParkingCardStatus status = ParkingCardStatus.AVAILABLE;
}
