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

/**
 * Entity lưu trữ Hình ảnh của phương tiện lúc ra/vào bãi.
 * Đây là bằng chứng quan trọng để đối chiếu xem lúc ra có đúng là xe lúc vào hay không.
 */
@Entity
@Getter
@Setter
@Table(name = "vehicle_image")
public class VehicleImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_image_id")
    private Long vehicleImageId;

    // Đường link xem ảnh trực tiếp (Thường là link từ Cloudinary)
    @Column(name = "image_url", nullable = false, columnDefinition = "NVARCHAR(1000)")
    private String imageUrl;

    // ID file ảnh trên Cloudinary (Dùng để có thể xóa ảnh sau này)
    @Column(name = "public_id",nullable = false,unique = true,columnDefinition = "NVARCHAR(500)")
    private String publicId;

    // Phân loại: Ảnh này chụp lúc nào? (CHECK_IN: Lúc vào, CHECK_OUT: Lúc ra, LICENSE_PLATE: Chụp cận cảnh biển số)
    @Enumerated(EnumType.STRING)
    @Column(name = "image_type",nullable = false,length = 30)
    private VehicleImageType imageType;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt; // Giờ upload

    // Thuộc về Lượt gửi xe (ParkingSession) nào?
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parking_session_id",nullable = false)
    private ParkingSession parkingSession;
}
