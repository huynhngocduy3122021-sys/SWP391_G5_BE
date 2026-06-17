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

@Entity
@Getter
@Setter
@Table(
    name = "parking_branch",
    uniqueConstraints = {
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
    private Long parkingBranchId;

    @Column(
        name = "branch_name",
        nullable = false,
        columnDefinition = "NVARCHAR(255)"
    )
    private String branchName;

    @Column(name = "address",nullable = false,columnDefinition = "NVARCHAR(500)")
    private String address;

    @Column(name = "phone_number",length = 20)
    private String phoneNumber;

    @Column( name = "description", columnDefinition = "NVARCHAR(500)")
    private String description;

    @Column( name = "active",nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "parkingBranch",cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ParkingFloor> parkingFloors = new ArrayList<>();

    @OneToMany( mappedBy = "parkingBranch", fetch = FetchType.LAZY)
    private List<ParkingCard> parkingCards = new ArrayList<>();
}

