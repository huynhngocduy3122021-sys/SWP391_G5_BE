package Parking.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * Entity đại diện cho Bảng giá / Chính sách giá gửi xe.
 * Ví dụ: Xe máy 5.000đ/4 tiếng đầu, mỗi 1 tiếng tiếp theo cộng thêm 2.000đ.
 */
@Entity
@Getter
@Setter
@Table(name = "price_policies")
public class PricePolicy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_policy_id")
    private Long pricePolicyId; // Mã chính sách giá

    @Column(name = "policy_name", nullable = false , columnDefinition = "NVARCHAR(255)")
    private String policyName; // Tên chính sách (Ví dụ: "Giá ô tô ban ngày")

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice; // Giá cơ bản (Giá khởi điểm khi xe vừa vào bãi)

    @Column(name = "base_duration_minutes", nullable = false)
    private Integer baseDurationMinutes; // Khoảng thời gian của giá cơ bản (Ví dụ: 240 phút = 4 tiếng đầu)

    @Column(name = "extra_hour_price", nullable = false)
    private BigDecimal extraHourPrice; // Giá phụ thu thêm (Ví dụ: 2000đ)

    @Column(name = "extra_duration_minutes", nullable = true)
    private Integer extraDurationMinutes = 60; // Khoảng thời gian phụ thu (Ví dụ: mỗi 60 phút tiếp theo)

    @Column(name = "active", nullable = false)
    private boolean active = true; // Chính sách này còn áp dụng không?

    // Chính sách giá này áp dụng cho Loại xe nào?
    @ManyToOne
    @JoinColumn(name = "vehicle_type_id")
    private VehicleType vehicleType;

    public Integer getExtraDurationMinutes() {
        return extraDurationMinutes == null ? 60 : extraDurationMinutes;
    }
}
