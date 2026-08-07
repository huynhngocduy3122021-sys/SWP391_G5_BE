package Parking.Service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.User;
import Parking.Model.MonthlyTicket;
import Parking.Model.MonthlyTicketRequest;
import Parking.Model.ParkingCard;
import Parking.Model.PricePolicy;
import Parking.Model.Vehicle;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.MonthlyTicketRequestRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.Repository.VehicleRepository;
import Parking.dto.request.CreateMonthlyTicketRequest;
import Parking.dto.request.UpdateMonthlyTicketRequest;
import Parking.dto.response.MonthlyCardLookupResponse;
import Parking.dto.response.MonthlyTicketResponse;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import Parking.enums.UserRole;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MonthlyTicketService {

    private final MonthlyTicketRepository monthlyTicketRepository;
    private final MonthlyTicketRequestRepository monthlyTicketRequestRepository;
    private final VehicleRepository vehicleRepository;
    private final ParkingCardRepository parkingCardRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final BranchScopeService branchScopeService;
    private final CurrentUserService currentUserService;

    @Transactional
    public MonthlyTicketResponse createMonthlyTicket(CreateMonthlyTicketRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new ParkingSessionException("Ngày bắt đầu và ngày kết thúc không được để trống");
        }
        if (request.getStartDate().isAfter(request.getEndDate()) || request.getStartDate().isEqual(request.getEndDate())) {
            throw new ParkingSessionException("Ngày bắt đầu phải trước ngày kết thúc");
        }

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy phương tiện"));

        ParkingCard parkingCard = parkingCardRepository.findByIdForUpdate(request.getParkingCardId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"));

        // Validate parking card properties
        boolean isEmployeeCard = parkingCard.getCardCode() != null && parkingCard.getCardCode().toUpperCase().startsWith("EMP-");
        if (parkingCard.getType() != ParkingCardType.MONTHLY && parkingCard.getType() != ParkingCardType.VIP && !isEmployeeCard) {
            throw new ParkingSessionException("Chỉ cho phép thẻ giữ xe loại tháng (MONTHLY), VIP hoặc nhân viên (EMPLOYEE) đăng ký vé");
        }
        if (parkingCard.getStatus() == ParkingCardStatus.LOST || parkingCard.getStatus() == ParkingCardStatus.DISABLED) {
            throw new ParkingSessionException("Thẻ giữ xe đang bị khóa hoặc báo mất");
        }
        if (parkingCard.getParkingBranch() == null) {
            throw new ParkingSessionException("Thẻ giữ xe chưa được gán vào chi nhánh nào");
        }
        if (!parkingCard.getParkingBranch().isActive()) {
            throw new ParkingSessionException("Chi nhánh của thẻ gửi xe này hiện không hoạt động");
        }

        // Branch scope authorization check
        branchScopeService.assertSameBranch(parkingCard.getParkingBranch().getParkingBranchId());

        // Overlapping monthly ticket validation
        if (request.getStatus() != null && request.getStatus() == 1) {
            if (monthlyTicketRepository.existsActiveOverlapByVehicle(vehicle.getVehiclesId(), request.getStartDate(), request.getEndDate(), null)) {
                throw new ParkingSessionException("Phương tiện này đã đăng ký vé tháng hoạt động trong khoảng thời gian này");
            }
            if (monthlyTicketRepository.existsActiveOverlapByCard(parkingCard.getParkingCardId(), request.getStartDate(), request.getEndDate(), null)) {
                throw new ParkingSessionException("Thẻ này đã đăng ký vé tháng hoạt động trong khoảng thời gian này");
            }
        }

        java.time.LocalDateTime finalEndDate = request.getEndDate();

        if (isEmployeeCard) {
            User employee = vehicle.getUser();
            if (employee == null) {
                throw new ParkingSessionException("Phương tiện phải được liên kết với một tài khoản để đăng ký thẻ nhân viên");
            }
            if (employee.getUserRole() == UserRole.USER) {
                 throw new ParkingSessionException("Tài khoản liên kết với phương tiện không phải là nhân viên");
            }
            if (request.getStatus() != null && request.getStatus() == 1) {
                if (monthlyTicketRepository.existsActiveEmployeeTicketByUserId(employee.getUserId(), null)) {
                    throw new ParkingSessionException("Nhân viên này đã có 1 thẻ đang hoạt động");
                }
            }
            finalEndDate = java.time.LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        }

        MonthlyTicket monthlyTicket = new MonthlyTicket();
        monthlyTicket.setVehicle(vehicle);
        monthlyTicket.setParkingCard(parkingCard);
        monthlyTicket.setGuestName(request.getGuestName());
        monthlyTicket.setGuestPhone(request.getGuestPhone());
        monthlyTicket.setStartDate(request.getStartDate());
        monthlyTicket.setEndDate(finalEndDate);
        monthlyTicket.setStatus(request.getStatus() != null ? Parking.enums.MonthlyTicketStatus.fromCode(request.getStatus()) : null);

        if (isEmployeeCard) {
            parkingCard.setType(ParkingCardType.EMPLOYEE);
        }
        parkingCard.setStatus(ParkingCardStatus.AVAILABLE);
        parkingCardRepository.save(parkingCard);

        return convertToResponse(monthlyTicketRepository.save(monthlyTicket));
    }

    @Transactional(readOnly = true)
    public List<MonthlyTicketResponse> getAllMonthlyTickets(Long branchId) {
        Long resolvedBranchId = branchScopeService.resolveReadableBranchId(branchId);
        return monthlyTicketRepository.findAllByBranchId(resolvedBranchId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonthlyTicketResponse> getMyMonthlyTickets() {
        User currentUser = currentUserService.getCurrentUser();
        return monthlyTicketRepository.findAllByUserId(currentUser.getUserId())
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyTicketResponse getMonthlyTicketById(Long id) {
        MonthlyTicket monthlyTicket = findMonthlyTicket(id);
        branchScopeService.assertSameBranch(monthlyTicket.getParkingCard().getParkingBranch().getParkingBranchId());
        return convertToResponse(monthlyTicket);
    }

    @Transactional
    public MonthlyTicketResponse updateMonthlyTicket(Long id, UpdateMonthlyTicketRequest request) {
        MonthlyTicket monthlyTicket = findMonthlyTicket(id);

        // Branch scope authorization check for existing branch
        branchScopeService.assertSameBranch(monthlyTicket.getParkingCard().getParkingBranch().getParkingBranchId());

        Vehicle vehicle = monthlyTicket.getVehicle();
        if (request.getVehicleId() != null) {
            vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy phương tiện"));
        }

        ParkingCard parkingCard = monthlyTicket.getParkingCard();
        if (request.getParkingCardId() != null) {
            parkingCard = parkingCardRepository.findByIdForUpdate(request.getParkingCardId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"));
        } else {
            parkingCard = parkingCardRepository.findByIdForUpdate(parkingCard.getParkingCardId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"));
        }

        // Validate parking card properties
        boolean isEmployeeCard = parkingCard.getCardCode() != null && parkingCard.getCardCode().toUpperCase().startsWith("EMP-");
        if (parkingCard.getType() != ParkingCardType.MONTHLY && parkingCard.getType() != ParkingCardType.VIP && !isEmployeeCard) {
            throw new ParkingSessionException("Chỉ cho phép thẻ giữ xe loại tháng (MONTHLY), VIP hoặc nhân viên (EMPLOYEE) đăng ký vé");
        }
        if (parkingCard.getStatus() == ParkingCardStatus.LOST || parkingCard.getStatus() == ParkingCardStatus.DISABLED) {
            throw new ParkingSessionException("Thẻ giữ xe đang bị khóa hoặc báo mất");
        }
        if (parkingCard.getParkingBranch() == null) {
            throw new ParkingSessionException("Thẻ giữ xe chưa được gán vào chi nhánh nào");
        }
        if (!parkingCard.getParkingBranch().isActive()) {
            throw new ParkingSessionException("Chi nhánh của thẻ gửi xe này hiện không hoạt động");
        }

        // Branch scope authorization check for final branch
        branchScopeService.assertSameBranch(parkingCard.getParkingBranch().getParkingBranchId());

        java.time.LocalDateTime startDate = request.getStartDate() != null ? request.getStartDate() : monthlyTicket.getStartDate();
        java.time.LocalDateTime endDate = request.getEndDate() != null ? request.getEndDate() : monthlyTicket.getEndDate();
        Integer status = request.getStatus() != null ? request.getStatus() : (monthlyTicket.getStatus() != null ? monthlyTicket.getStatus().getCode() : null);

        if (isEmployeeCard) {
            User employee = vehicle.getUser();
            if (employee == null) {
                throw new ParkingSessionException("Phương tiện phải được liên kết với một tài khoản để cập nhật thẻ nhân viên");
            }
            if (employee.getUserRole() == UserRole.USER) {
                 throw new ParkingSessionException("Tài khoản liên kết với phương tiện không phải là nhân viên");
            }
            if (status != null && status == 1) {
                if (monthlyTicketRepository.existsActiveEmployeeTicketByUserId(employee.getUserId(), id)) {
                    throw new ParkingSessionException("Nhân viên này đã có 1 thẻ đang hoạt động");
                }
            }
            endDate = java.time.LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        } else {
            if (startDate.isAfter(endDate) || startDate.isEqual(endDate)) {
                throw new ParkingSessionException("Ngày bắt đầu phải trước ngày kết thúc");
            }
        }

        // Overlapping monthly ticket validation (exclude current ticket ID)
        if (status != null && status == 1) {
            if (monthlyTicketRepository.existsActiveOverlapByVehicle(vehicle.getVehiclesId(), startDate, endDate, id)) {
                throw new ParkingSessionException("Phương tiện này đã đăng ký vé tháng hoạt động trong khoảng thời gian này");
            }
            if (monthlyTicketRepository.existsActiveOverlapByCard(parkingCard.getParkingCardId(), startDate, endDate, id)) {
                throw new ParkingSessionException("Thẻ này đã đăng ký vé tháng hoạt động trong khoảng thời gian này");
            }
        }

        monthlyTicket.setVehicle(vehicle);
        monthlyTicket.setParkingCard(parkingCard);

        if (request.getGuestName() != null) {
            monthlyTicket.setGuestName(request.getGuestName());
        }

        if (request.getGuestPhone() != null) {
            monthlyTicket.setGuestPhone(request.getGuestPhone());
        }

        monthlyTicket.setStartDate(startDate);
        monthlyTicket.setEndDate(endDate);
        monthlyTicket.setStatus(status != null ? Parking.enums.MonthlyTicketStatus.fromCode(status) : null);

        if (isEmployeeCard) {
            parkingCard.setType(ParkingCardType.EMPLOYEE);
        }
        parkingCard.setStatus(ParkingCardStatus.AVAILABLE);
        parkingCardRepository.save(parkingCard);

        return convertToResponse(monthlyTicketRepository.save(monthlyTicket));
    }

    @Transactional
    public void deleteMonthlyTicket(Long id) {
        MonthlyTicket ticket = findMonthlyTicket(id);
        ParkingCard card = ticket.getParkingCard();

        branchScopeService.assertSameBranch(card.getParkingBranch().getParkingBranchId());

        if (ticket.getStatus() != Parking.enums.MonthlyTicketStatus.INACTIVE) {
            throw new Parking.exception.exceptions.InvalidTicketStateException("Phải dừng vé tháng trước khi xóa");
        }

        if (parkingSessionRepository.existsByParkingCardParkingCardIdAndStatus(
                card.getParkingCardId(), Parking.enums.ParkingSessionStatus.ACTIVE)) {
            throw new Parking.exception.exceptions.InvalidTicketStateException("Không thể xóa vé khi thẻ đang có phiên gửi xe hoạt động");
        }

        if (monthlyTicketRequestRepository.existsByRenewalOfTicketTicketIdAndStatusIn(
                id,
                List.of(Parking.enums.MonthlyTicketRequestStatus.PENDING_PAYMENT,
                        Parking.enums.MonthlyTicketRequestStatus.PENDING_APPROVAL))) {
            throw new Parking.exception.exceptions.InvalidTicketStateException("Không thể xóa vé khi còn yêu cầu gia hạn đang xử lý");
        }

        ticket.setDeleted(true);
        ticket.setDeletedAt(java.time.LocalDateTime.now());
        ticket.setDeletedBy(currentUserService.getCurrentUser());
        ticket.setStatus(Parking.enums.MonthlyTicketStatus.INACTIVE);
        monthlyTicketRepository.save(ticket);

        if (card.getStatus() != ParkingCardStatus.LOST && card.getStatus() != ParkingCardStatus.DISABLED) {
            card.setStatus(ParkingCardStatus.AVAILABLE);
            parkingCardRepository.save(card);
        }
    }

    @Transactional
    public void stopMonthlyTicket(Long ticketId) {
        MonthlyTicket ticket = findMonthlyTicket(ticketId);
        ParkingCard card = ticket.getParkingCard();

        branchScopeService.assertSameBranch(
                card.getParkingBranch().getParkingBranchId());

        if (parkingSessionRepository
                .existsByParkingCardParkingCardIdAndStatus(
                        card.getParkingCardId(), Parking.enums.ParkingSessionStatus.ACTIVE)) {
            throw new Parking.exception.exceptions.InvalidTicketStateException(
                    "Không thể dừng vé khi thẻ đang có phiên gửi xe hoạt động");
        }

        ticket.setStatus(Parking.enums.MonthlyTicketStatus.INACTIVE);
        monthlyTicketRepository.save(ticket);

        if (card.getStatus() != ParkingCardStatus.LOST
                && card.getStatus() != ParkingCardStatus.DISABLED) {
            card.setStatus(ParkingCardStatus.AVAILABLE);
            parkingCardRepository.save(card);
        }
    }

    @Transactional(readOnly = true)
    public MonthlyCardLookupResponse lookupByCardCode(String rawCardCode, java.time.LocalDateTime requestedTime) {
        String cardCode = rawCardCode == null ? "" : rawCardCode.trim().toUpperCase();
        java.time.LocalDateTime time = requestedTime != null ? requestedTime : java.time.LocalDateTime.now();

        ParkingCard card = parkingCardRepository.findByCardCodeIgnoreCase(cardCode)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"));

        branchScopeService.assertSameBranch(card.getParkingBranch().getParkingBranchId());

        List<MonthlyTicket> tickets = monthlyTicketRepository.findAllByCardOrderByNewest(card.getParkingCardId());

        if (tickets.isEmpty()) {
            return baseLookup(card, "NOT_ASSIGNED", "Thẻ tháng chưa được liên kết với vé nào", null);
        }

        List<MonthlyTicket> activeTickets = tickets.stream()
                .filter(ticket -> ticket.getStatus() == Parking.enums.MonthlyTicketStatus.ACTIVE)
                .filter(ticket -> !ticket.getStartDate().isAfter(time))
                .filter(ticket -> !ticket.getEndDate().isBefore(time))
                .toList();

        if (activeTickets.size() > 1) {
            throw new Parking.exception.exceptions.InvalidTicketStateException("Dữ liệu không hợp lệ: thẻ có nhiều vé tháng đang hoạt động");
        }

        if (activeTickets.size() == 1) {
            return activeLookup(card, activeTickets.get(0));
        }

        MonthlyTicket latest = tickets.get(0);

        if (latest.getStatus() == Parking.enums.MonthlyTicketStatus.INACTIVE) {
            return baseLookup(card, "STOPPED", "Thẻ tháng không khả dụng", latest);
        }

        if (latest.getEndDate() != null && latest.getEndDate().isBefore(time)) {
            return baseLookup(card, "EXPIRED", "Thẻ tháng đã hết hạn", latest);
        }

        if (latest.getStartDate() != null && latest.getStartDate().isAfter(time)) {
            return baseLookup(card, "NOT_STARTED", "Thẻ tháng chưa đến ngày hiệu lực", latest);
        }

        return baseLookup(card, "NOT_ASSIGNED", "Thẻ tháng không có vé hợp lệ", latest);
    }

    private MonthlyCardLookupResponse baseLookup(ParkingCard card, String status, String message, MonthlyTicket latest) {
        return MonthlyCardLookupResponse.builder()
                .lookupStatus(status)
                .message(message)
                .parkingCardId(card.getParkingCardId())
                .cardCode(card.getCardCode())
                .ticketId(latest != null ? latest.getTicketId() : null)
                .endDate(latest != null ? latest.getEndDate() : null)
                .build();
    }

    private MonthlyCardLookupResponse activeLookup(ParkingCard card, MonthlyTicket ticket) {
        return MonthlyCardLookupResponse.builder()
                .lookupStatus("ACTIVE")
                .message("Thẻ tháng hợp lệ")
                .ticketId(ticket.getTicketId())
                .parkingCardId(card.getParkingCardId())
                .cardCode(card.getCardCode())
                .vehicleId(ticket.getVehicle().getVehiclesId())
                .licensePlate(ticket.getVehicle().getLicensePlate())
                .vehicleColor(ticket.getVehicle().getVehicleColor())
                .vehicleBrand(ticket.getVehicle().getVehicleBrand())
                .vehicleTypeId(ticket.getVehicle().getVehicleType().getVehicleTypeId())
                .startDate(ticket.getStartDate())
                .endDate(ticket.getEndDate())
                .build();
    }

    private MonthlyTicket findMonthlyTicket(Long id) {
        return monthlyTicketRepository.findByTicketIdAndDeletedFalse(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy vé tháng"));
    }

    private MonthlyTicketResponse convertToResponse(MonthlyTicket monthlyTicket) {
        PricePolicy pricePolicy = monthlyTicket.getPricePolicy();
        MonthlyTicketRequest request = monthlyTicket.getMonthlyTicketRequest();

        // Tương thích dữ liệu vé cũ được cấp trước khi monthly_ticket có
        // price_policy_id/monthly_ticket_request_id. Chỉ lấy request đã thanh
        // toán của đúng xe, đúng chi nhánh và được tạo trước thời điểm cấp vé.
        if (pricePolicy == null) {
            MonthlyTicketRequest issuedRequest = request != null
                    ? request
                    : monthlyTicketRequestRepository.findBestIssuedRequestForTicket(
                        monthlyTicket.getVehicle().getVehiclesId(),
                        monthlyTicket.getParkingCard().getParkingBranch().getParkingBranchId(),
                        monthlyTicket.getCreatedAt()
                    ).orElse(null);

            if (issuedRequest != null) {
                request = issuedRequest;
                pricePolicy = issuedRequest.getPricePolicy();
            }
        }

        return MonthlyTicketResponse.builder()
                .ticketId(monthlyTicket.getTicketId())
                .vehicleId(monthlyTicket.getVehicle().getVehiclesId())
                .vehicleTypeId(monthlyTicket.getVehicle().getVehicleType() != null ? monthlyTicket.getVehicle().getVehicleType().getVehicleTypeId() : null)
                .licensePlate(monthlyTicket.getVehicle().getLicensePlate())
                .parkingCardId(monthlyTicket.getParkingCard().getParkingCardId())
                .cardCode(monthlyTicket.getParkingCard().getCardCode())
                .guestName(monthlyTicket.getGuestName())
                .guestPhone(monthlyTicket.getGuestPhone())
                .startDate(monthlyTicket.getStartDate())
                .endDate(monthlyTicket.getEndDate())
                .parkingBranchId(monthlyTicket.getParkingCard().getParkingBranch().getParkingBranchId())
                .parkingBranchName(monthlyTicket.getParkingCard().getParkingBranch().getBranchName())
                .status(monthlyTicket.getStatus() != null ? monthlyTicket.getStatus().getCode() : null)
                .createdAt(monthlyTicket.getCreatedAt())
                .pricePolicyId(pricePolicy != null ? pricePolicy.getPricePolicyId() : null)
                .pricePolicy(pricePolicy != null ? MonthlyTicketResponse.PricePolicySummary.builder()
                        .pricePolicyId(pricePolicy.getPricePolicyId())
                        .policyName(pricePolicy.getPolicyName())
                        .vehicleTypeId(pricePolicy.getVehicleType() != null ? pricePolicy.getVehicleType().getVehicleTypeId() : null)
                        .vehicleTypeName(pricePolicy.getVehicleType() != null ? pricePolicy.getVehicleType().getTypeName() : null)
                        .build() : null)
                .monthlyTicketRequestId(request != null ? request.getId() : null)
                .build();
    }
}
