package Parking.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import Parking.Model.MonthlyTicketRequest;

@Repository
public interface MonthlyTicketRequestRepository extends JpaRepository<MonthlyTicketRequest, Long> {
    List<MonthlyTicketRequest> findByStatus(Integer status);
    List<MonthlyTicketRequest> findByUserUserId(Long userId);
}
