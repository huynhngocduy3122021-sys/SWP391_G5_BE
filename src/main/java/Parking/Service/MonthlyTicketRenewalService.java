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

    @Transactional
    public MonthlyTicketRequest createRenewalRequest(Long ticketId, CreateRenewalRequest dto) {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser == null) {
            throw new ResourceNotFoundException("User not found");
        }

        MonthlyTicket ticket = ticketRepo.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé tháng"));

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

        return requestRepo.save(request);
    }
}
