package Parking.Model;

import java.time.LocalDateTime;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "vehicle_image")
public class VehicleImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_image_id")
    private Long vehicleImageId;

    @Column(name = "image_url", nullable = false, columnDefinition = "NVARCHAR(1000)")
    private String imageUrl;

    @Column(name = "public_id",nullable = false,unique = true,columnDefinition = "NVARCHAR(500)")
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type",nullable = false,length = 30)
    private VehicleImageType imageType;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parking_session_id",nullable = false)
    private ParkingSession parkingSession;
}
