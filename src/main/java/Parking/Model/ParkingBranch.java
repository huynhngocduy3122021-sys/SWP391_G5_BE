package Parking.Model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity đại diện cho một Chi nhánh bãi đỗ xe (Ví dụ: Bãi đỗ xe Vincom Quận 1, Bãi đỗ xe sân bay).
 * Đây là thực thể cấp cao nhất trong cấu trúc Cơ sở vật chất.
 */
@Entity
@Getter
@Setter
@Table(
    name = "parking_branch",
    uniqueConstraints = {
        // Tên chi nhánh không được phép trùng nhau
        @UniqueConstraint(
            name = "uk_parking_branch_name",
            columnNames = "branch_name"
        )
    }
)
public class ParkingBranch {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parking_branch_id")
    private Long parkingBranchId; // Mã chi nhánh

    @Column(
        name = "branch_name",
        nullable = false,
        columnDefinition = "NVARCHAR(255)"
    )
    private String branchName; // Tên chi nhánh

    @Column(name = "address",nullable = false,columnDefinition = "NVARCHAR(500)")
    private String address; // Địa chỉ chi nhánh

    @Column(name = "phone_number",length = 20)
    private String phoneNumber; // Số điện thoại liên hệ của chi nhánh

    @Column( name = "description", columnDefinition = "NVARCHAR(500)")
    private String description; // Mô tả thêm

    @Column( name = "active",nullable = false)
    private boolean active = true; // Chi nhánh có đang hoạt động hay không?

    // Một chi nhánh có nhiều Tầng (ParkingFloor)
    @OneToMany(mappedBy = "parkingBranch",cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ParkingFloor> parkingFloors = new ArrayList<>();

    // Một chi nhánh quản lý nhiều Thẻ giữ xe vật lý
    @OneToMany( mappedBy = "parkingBranch", fetch = FetchType.LAZY)
    private List<ParkingCard> parkingCards = new ArrayList<>();

    // Một chi nhánh có nhiều Nhân viên đang làm việc
    @OneToMany(mappedBy = "parkingBranch", fetch = FetchType.LAZY)
    private List<User> staffList = new ArrayList<>();
}

