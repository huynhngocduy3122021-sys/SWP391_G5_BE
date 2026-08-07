package Parking.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.IncidentLog;
import Parking.Model.IncidentReport;
import Parking.Model.MonthlyTicket;
import Parking.Model.ParkingCard;
import Parking.Model.User;
import Parking.Repository.IncidentReportRepository;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.dto.response.IncidentReportResponse;
import Parking.enums.IncidentLogAction;
import Parking.enums.IncidentStatus;
import Parking.enums.IncidentType;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingSessionStatus;
import Parking.exception.exceptions.InvalidTicketStateException;
import Parking.exception.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LostMonthlyCardReplacementService {
    private final IncidentReportRepository incidentRepository;
    private final ParkingCardRepository cardRepository;
    private final MonthlyTicketRepository ticketRepository;
    private final ParkingSessionRepository sessionRepository;
    private final BranchScopeService branchScopeService;
    private final CurrentUserService currentUserService;
    private final IncidentReportService incidentReportService;

    @Transactional
    public IncidentReportResponse replaceCard(
            Long incidentId,
            Long replacementCardId) {

        IncidentReport incident = incidentRepository
                .findByIdForUpdate(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy báo cáo mất thẻ"));

        if (incident.getIncidentType() != IncidentType.LOST_CARD) {
            throw new InvalidTicketStateException(
                    "Sự cố này không phải báo mất thẻ");
        }

        if (incident.getStatus() == IncidentStatus.RESOLVED
                || incident.getStatus() == IncidentStatus.CANCELLED) {
            throw new InvalidTicketStateException(
                    "Sự cố đã kết thúc, không thể cấp thẻ thay thế");
        }

        if (incident.getReplacementCard() != null) {
            throw new InvalidTicketStateException(
                    "Sự cố này đã được cấp thẻ thay thế");
        }

        ParkingCard oldCard = cardRepository
                .findByIdForUpdate(incident.getParkingCard().getParkingCardId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thẻ bị mất"));

        if (oldCard.getStatus() != ParkingCardStatus.LOST) {
            throw new InvalidTicketStateException(
                    "Thẻ cũ chưa được khóa ở trạng thái LOST");
        }

        branchScopeService.assertSameBranch(
                oldCard.getParkingBranch().getParkingBranchId());

        if (sessionRepository
                .existsByParkingCardParkingCardIdAndStatus(
                        oldCard.getParkingCardId(),
                        ParkingSessionStatus.ACTIVE)) {
            throw new InvalidTicketStateException(
                    "Phải checkout xe và hoàn tất xử lý mất thẻ trước khi cấp thẻ mới");
        }

        List<MonthlyTicket> activeTickets = ticketRepository
                .findActiveTicketsForLostCard(
                        oldCard.getParkingCardId(), LocalDateTime.now());

        if (activeTickets.isEmpty()) {
            throw new InvalidTicketStateException(
                    "Thẻ bị mất không có vé tháng còn hiệu lực");
        }
        if (activeTickets.size() > 1) {
            throw new InvalidTicketStateException(
                    "Dữ liệu không hợp lệ: thẻ cũ có nhiều vé đang hoạt động");
        }

        MonthlyTicket ticket = activeTickets.get(0);

        ParkingCard newCard = cardRepository
                .findByIdForUpdate(replacementCardId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thẻ thay thế"));

        if (newCard.getStatus() != ParkingCardStatus.AVAILABLE) {
            throw new InvalidTicketStateException(
                    "Thẻ thay thế không khả dụng");
        }

        if (!oldCard.getParkingBranch().getParkingBranchId().equals(
                newCard.getParkingBranch().getParkingBranchId())) {
            throw new InvalidTicketStateException(
                    "Thẻ thay thế phải cùng chi nhánh với thẻ bị mất");
        }

        if (oldCard.getType() != newCard.getType()) {
            throw new InvalidTicketStateException(
                    "Loại thẻ thay thế không khớp với thẻ bị mất");
        }

        if (ticketRepository.existsActiveTicketByCard(
                newCard.getParkingCardId(), LocalDateTime.now())) {
            throw new InvalidTicketStateException(
                    "Thẻ thay thế đang được liên kết với vé tháng khác");
        }

        ticket.setParkingCard(newCard);
        ticketRepository.save(ticket);

        // AVAILABLE nghĩa là thẻ đã được cấp cho thuê bao nhưng xe chưa ở trong bãi.
        newCard.setStatus(ParkingCardStatus.AVAILABLE);
        cardRepository.save(newCard);

        // Tuyệt đối không mở lại thẻ cũ.
        oldCard.setStatus(ParkingCardStatus.LOST);
        cardRepository.save(oldCard);

        User manager = currentUserService.getCurrentUser();
        incident.setReplacementCard(newCard);
        incident.setReplacementTicket(ticket);
        incident.setReplacementAt(LocalDateTime.now());
        incident.setReplacementBy(manager);

        IncidentLog log = new IncidentLog();
        log.setChangedBy(manager);
        log.setChangedAt(LocalDateTime.now());
        log.setOldStatus(incident.getStatus());
        log.setNewStatus(incident.getStatus());
        log.setActionType(IncidentLogAction.UPDATE_STATUS);
        log.setDescription(
                "Đã thay thẻ " + oldCard.getCardCode()
                + " bằng " + newCard.getCardCode()
                + " cho vé tháng #" + ticket.getTicketId());
        incident.addLog(log);

        return incidentReportService.convertToResponse(incidentRepository.save(incident));
    }
}
