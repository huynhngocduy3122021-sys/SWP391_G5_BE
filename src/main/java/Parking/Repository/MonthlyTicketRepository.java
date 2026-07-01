package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import Parking.Model.MonthlyTicket;

public interface MonthlyTicketRepository extends JpaRepository<MonthlyTicket, Long> {
}
