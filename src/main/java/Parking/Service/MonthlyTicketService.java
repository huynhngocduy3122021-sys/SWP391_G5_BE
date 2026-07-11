package Parking.Service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.User;
import Parking.Model.MonthlyTicket;
import Parking.Model.ParkingCard;
import Parking.Model.Vehicle;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.VehicleRepository;
import Parking.dto.request.CreateMonthlyTicketRequest;
import Parking.dto.request.UpdateMonthlyTicketRequest;
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
    private final VehicleRepository vehicleRepository;
    private final ParkingCardRepository parkingCardRepository;
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

        ParkingCard parkingCard = parkingCardRepository.findById(request.getParkingCardId())
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
        monthlyTicket.setStatus(request.getStatus());

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
            parkingCard = parkingCardRepository.findById(request.getParkingCardId())
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
        Integer status = request.getStatus() != null ? request.getStatus() : monthlyTicket.getStatus();

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
        monthlyTicket.setStatus(status);

        if (isEmployeeCard) {
            parkingCard.setType(ParkingCardType.EMPLOYEE);
        }
        parkingCard.setStatus(ParkingCardStatus.AVAILABLE);
        parkingCardRepository.save(parkingCard);

        return convertToResponse(monthlyTicketRepository.save(monthlyTicket));
    }

    @Transactional
    public void deleteMonthlyTicket(Long id) {
        MonthlyTicket monthlyTicket = findMonthlyTicket(id);
        branchScopeService.assertSameBranch(monthlyTicket.getParkingCard().getParkingBranch().getParkingBranchId());
        monthlyTicketRepository.delete(monthlyTicket);
    }

    private MonthlyTicket findMonthlyTicket(Long id) {
        return monthlyTicketRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy vé tháng"));
    }

    private MonthlyTicketResponse convertToResponse(MonthlyTicket monthlyTicket) {
        return MonthlyTicketResponse.builder()
                .ticketId(monthlyTicket.getTicketId())
                .vehicleId(monthlyTicket.getVehicle().getVehiclesId())
                .licensePlate(monthlyTicket.getVehicle().getLicensePlate())
                .parkingCardId(monthlyTicket.getParkingCard().getParkingCardId())
                .cardCode(monthlyTicket.getParkingCard().getCardCode())
                .guestName(monthlyTicket.getGuestName())
                .guestPhone(monthlyTicket.getGuestPhone())
                .startDate(monthlyTicket.getStartDate())
                .endDate(monthlyTicket.getEndDate())
                .parkingBranchId(monthlyTicket.getParkingCard().getParkingBranch().getParkingBranchId())
                .parkingBranchName(monthlyTicket.getParkingCard().getParkingBranch().getBranchName())
                .status(monthlyTicket.getStatus())
                .build();
    }
}
