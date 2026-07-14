package Parking.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Entity lưu trữ Hình ảnh bằng chứng của các Sự cố.
 * Ví dụ: Chụp ảnh xe bị xước để làm bằng chứng bồi thường.
 */
@Entity
@Getter
@Setter
@Table(name = "incident_images")
public class IncidentImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_image_id")
    private Long incidentImageId;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl; // Đường dẫn ảnh (Cloudinary)

    @Column(name = "public_id")
    private String publicId; // Dùng để xóa ảnh trên Cloudinary khi cần

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    // Ai là người chụp/upload bức ảnh này?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    // Ảnh này thuộc về Báo cáo sự cố nào?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private IncidentReport incidentReport;
}
