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

@Entity
@Getter
@Setter
@Table(name = "price_policies")
public class PricePolicy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_policy_id")
    private Long pricePolicyId;

    @Column(name = "policy_name", nullable = false , columnDefinition = "NVARCHAR(255)")
    private String policyName;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "base_duration_minutes", nullable = false)
    private Integer baseDurationMinutes;

    @Column(name = "extra_hour_price", nullable = false)
    private BigDecimal extraHourPrice;

    @Column(name = "extra_duration_minutes", nullable = true)
    private Integer extraDurationMinutes = 60;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToOne
    @JoinColumn(name = "vehicle_type_id")
    private VehicleType vehicleType;

    public Integer getExtraDurationMinutes() {
        return extraDurationMinutes == null ? 60 : extraDurationMinutes;
    }
}
