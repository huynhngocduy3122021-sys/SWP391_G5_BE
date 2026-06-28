package Parking.Repository;

import Parking.Model.IncidentImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentImageRepository extends JpaRepository<IncidentImage, Long> {
}
