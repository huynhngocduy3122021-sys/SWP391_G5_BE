package Parking.Repository;

import Parking.Model.IncidentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentLogRepository extends JpaRepository<IncidentLog, Long> {
}
