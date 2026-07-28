package Parking.Repository;

import Parking.Model.IncidentImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentImageRepository extends JpaRepository<IncidentImage, Long> {
    List<IncidentImage> findByIncidentReportIncidentIdOrderByUploadedAtAsc(Long incidentId);
    long countByIncidentReportIncidentId(Long incidentId);
    Optional<IncidentImage> findByIncidentImageIdAndIncidentReportIncidentId(Long imageId, Long incidentId);
}
