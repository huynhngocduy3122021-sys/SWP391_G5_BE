package Parking.Service;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import Parking.Model.MonthlyTicket;
import Parking.Model.MonthlyTicketRequest;
import Parking.Model.PricePolicy;
import Parking.Model.ParkingBranch;
import Parking.Model.User;
import Parking.Model.Vehicle;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.PricePolicyRepository;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.MonthlyTicketRequestRepository;
import Parking.dto.request.CreateRenewalRequest;
import Parking.dto.response.MonthlyTicketRequestResponse;
import Parking.enums.MonthlyTicketRequestStatus;
import Parking.exception.exceptions.ResourceNotFoundException;
import Parking.exception.exceptions.ForbiddenOperationException;
import Parking.exception.exceptions.InvalidTicketStateException;

@Service
@RequiredArgsConstructor
public class MonthlyTicketRenewalService {

    private final MonthlyTicketRepository ticketRepo;
    private final PricePolicyRepository policyRepo;
    private final ParkingBranchRepository branchRepo;
    private final MonthlyTicketRequestRepository requestRepo;
    private final CurrentUserService currentUserService;
    private final Parking.Repository.UserRepository userRepo;
    private final MonthlyTicketRequestService monthlyTicketRequestService;

    private static final java.util.List<MonthlyTicketRequestStatus> OPEN_REQUEST_STATUSES = java.util.List.of(
            MonthlyTicketRequestStatus.PENDING_PAYMENT,
            MonthlyTicketRequestStatus.PENDING_APPROVAL
    );

    @Transactional
    public MonthlyTicketRequestResponse createRenewalRequest(Long ticketId, CreateRenewalRequest dto) {
        User authenticatedUser = currentUserService.getCurrentUser();
        if (authenticatedUser == null) {
            throw new ResourceNotFoundException("User not found");
        }

        User currentUser = userRepo.findByIdForUpdate(authenticatedUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (requestRepo.existsOpenRequestByUser(
                currentUser.getUserId(), OPEN_REQUEST_STATUSES)) {
            throw new InvalidTicketStateException(
                    "Bạn đang có yêu cầu thẻ tháng chưa hoàn tất. "
                    + "Không thể tạo thêm yêu cầu gia hạn."
            );
        }

        if (requestRepo.existsByRenewalOfTicketTicketIdAndStatusIn(
                ticketId, OPEN_REQUEST_STATUSES)) {
            throw new InvalidTicketStateException(
                    "Vé tháng này đã có yêu cầu gia hạn đang xử lý."
            );
        }

        MonthlyTicket ticket = ticketRepo.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé tháng"));

        if (ticket.getStatus() != Parking.enums.MonthlyTicketStatus.ACTIVE && ticket.getStatus() != Parking.enums.MonthlyTicketStatus.INACTIVE) {
            throw new InvalidTicketStateException("Trạng thái vé tháng không hợp lệ để gia hạn.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (ticket.getEndDate() == null) {
            throw new InvalidTicketStateException("Vé tháng không có ngày hết hạn hợp lệ.");
        }
        if (ticket.getEndDate().isBefore(now.minusDays(30))) {
            throw new InvalidTicketStateException(
                    "Vé đã hết hạn quá thời gian cho phép gia hạn. Vui lòng đăng ký gói mới."
            );
        }

        if (ticket.getParkingCard() == null || ticket.getParkingCard().getParkingBranch() == null) {
            throw new InvalidTicketStateException("Vé tháng không có thông tin chi nhánh hợp lệ.");
        }

        Long currentBranchId = ticket.getParkingCard().getParkingBranch().getParkingBranchId();
        if (!currentBranchId.equals(dto.getBranchId())) {
            throw new InvalidTicketStateException("Gia hạn phải thực hiện tại chi nhánh của vé hiện tại.");
        }

        if (!ticket.getVehicle().getUser().getUserId().equals(currentUser.getUserId())) {
            throw new ForbiddenOperationException("Bạn không sở hữu vé tháng này");
        }

        PricePolicy policy = policyRepo.findById(dto.getPolicyId())
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ"));

        PricePolicy currentPolicy = ticket.getPricePolicy();
        if (currentPolicy == null) {
            MonthlyTicketRequest issuedRequest = ticket.getMonthlyTicketRequest();
            if (issuedRequest == null) {
                issuedRequest = requestRepo.findBestIssuedRequestForTicket(
                    ticket.getVehicle().getVehiclesId(),
                    ticket.getParkingCard().getParkingBranch().getParkingBranchId(),
                    ticket.getCreatedAt()
                ).orElse(null);
            }

            if (issuedRequest != null && issuedRequest.getPricePolicy() != null) {
                currentPolicy = issuedRequest.getPricePolicy();
                ticket.setPricePolicy(currentPolicy);
                ticketRepo.save(ticket);
            }
        }

        if (currentPolicy == null || currentPolicy.getPricePolicyId() == null) {
            throw new InvalidTicketStateException("Không xác định được gói hiện tại của vé. Vui lòng liên hệ quản lý để kiểm tra");
        }

        if (!currentPolicy.getPricePolicyId().equals(policy.getPricePolicyId())) {
            throw new InvalidTicketStateException("Chỉ được gia hạn đúng gói dịch vụ hiện tại: " + currentPolicy.getPolicyName());
        }

        if (!policy.isActive()) {
            throw new InvalidTicketStateException("Gói dịch vụ hiện tại đã ngừng hoạt động");
        }

        ParkingBranch branch = branchRepo.findById(dto.getBranchId())
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh"));

        Vehicle vehicle = ticket.getVehicle();

        if (vehicle.getVehicleType() == null || policy.getVehicleType() == null ||
            !vehicle.getVehicleType().getVehicleTypeId().equals(policy.getVehicleType().getVehicleTypeId())) {
            throw new InvalidTicketStateException("Không thể gia hạn bằng gói của loại phương tiện khác");
        }

        MonthlyTicketRequest request = new MonthlyTicketRequest();
        request.setVehicle(vehicle);
        request.setPricePolicy(policy);
        request.setParkingBranch(branch);
        request.setUser(currentUser);
        request.setStatus(MonthlyTicketRequestStatus.PENDING_PAYMENT);
        request.setCreatedAt(LocalDateTime.now());
        request.setRenewalOfTicket(ticket);

        MonthlyTicketRequest savedRequest = requestRepo.save(request);
        return monthlyTicketRequestService.toResponse(savedRequest);
    }
}
