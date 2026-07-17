package Parking.Service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import Parking.Model.MonthlyTicketRequest;
import Parking.Model.User;
import Parking.Model.Vehicle;
import Parking.Model.PricePolicy;
import Parking.Model.ParkingBranch;
import Parking.Model.MonthlyTicket;
import Parking.Model.ParkingCard;
import Parking.Model.Payment;
import Parking.Repository.MonthlyTicketRequestRepository;
import Parking.Repository.VehicleRepository;
import Parking.Repository.PricePolicyRepository;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.enums.MonthlyTicketRequestStatus;
import Parking.enums.MonthlyTicketStatus;
import Parking.enums.ParkingCardStatus;
import Parking.enums.PaymentStatus;
import Parking.dto.request.SubmitMonthlyTicketRequest;
import Parking.exception.exceptions.ResourceNotFoundException;
import Parking.exception.exceptions.ForbiddenOperationException;
import Parking.exception.exceptions.InvalidTicketStateException;

@Service
@RequiredArgsConstructor
public class MonthlyTicketRequestService {

    private final MonthlyTicketRequestRepository requestRepo;
    private final VehicleRepository vehicleRepo;
    private final PricePolicyRepository policyRepo;
    private final ParkingBranchRepository branchRepo;
    private final MonthlyTicketRepository monthlyTicketRepo;
    private final ParkingCardRepository parkingCardRepo;
    private final CurrentUserService currentUserService;

    public MonthlyTicketRequest submitRequest(SubmitMonthlyTicketRequest req) {
        User user = currentUserService.getCurrentUser();
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        Vehicle vehicle = vehicleRepo.findById(req.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        PricePolicy policy = policyRepo.findById(req.getPolicyId())
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
        ParkingBranch branch = branchRepo.findById(req.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (vehicle.getUser() == null || !vehicle.getUser().getUserId().equals(user.getUserId())) {
            throw new ForbiddenOperationException("Phương tiện không thuộc tài khoản hiện tại");
        }
        if (vehicle.isDeleted()) {
            throw new InvalidTicketStateException("Phương tiện đã bị xóa");
        }
        if (!policy.isActive()) {
            throw new InvalidTicketStateException("Gói dịch vụ đã ngừng hoạt động");
        }
        if (vehicle.getVehicleType() == null || policy.getVehicleType() == null ||
            !vehicle.getVehicleType().getVehicleTypeId().equals(policy.getVehicleType().getVehicleTypeId())) {
            throw new InvalidTicketStateException("Loại phương tiện không phù hợp với gói dịch vụ");
        }

        MonthlyTicketRequest mtr = new MonthlyTicketRequest();
        mtr.setUser(user);
        mtr.setVehicle(vehicle);
        mtr.setPricePolicy(policy);
        mtr.setParkingBranch(branch);
        mtr.setStatus(MonthlyTicketRequestStatus.PENDING_PAYMENT);
        mtr.setCreatedAt(LocalDateTime.now());
        
        return requestRepo.save(mtr);
    }

    public List<MonthlyTicketRequest> getAllRequests() {
        return requestRepo.findAll();
    }

    public List<MonthlyTicketRequest> getMyRequests() {
        User user = currentUserService.getCurrentUser();
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }
        return requestRepo.findByUserUserId(user.getUserId());
    }

    @Transactional
    public MonthlyTicketRequest updateStatus(Long id, MonthlyTicketRequestStatus status) {
        MonthlyTicketRequest req = requestRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        
        if (status == MonthlyTicketRequestStatus.APPROVED) {
            if (req.getStatus() != MonthlyTicketRequestStatus.PENDING_APPROVAL) {
                throw new InvalidTicketStateException("Yêu cầu không ở trạng thái chờ duyệt.");
            }
            
            Vehicle vehicle = req.getVehicle();
            PricePolicy policy = req.getPricePolicy();
            if (vehicle.getVehicleType() == null || policy.getVehicleType() == null ||
                !vehicle.getVehicleType().getVehicleTypeId().equals(policy.getVehicleType().getVehicleTypeId())) {
                throw new InvalidTicketStateException("Dữ liệu yêu cầu không hợp lệ: loại xe không khớp gói.");
            }

            Payment payment = req.getPayment();
            if (payment == null || payment.getPaymentStatus() != PaymentStatus.PAID) {
                throw new InvalidTicketStateException("Yêu cầu này chưa được thanh toán thành công.");
            }
            
            MonthlyTicket oldTicket = req.getRenewalOfTicket();
            if (oldTicket != null) {
                if (!oldTicket.getVehicle().getVehiclesId().equals(req.getVehicle().getVehiclesId())) {
                    throw new InvalidTicketStateException("Xe gia hạn không khớp vé hiện tại");
                }
                PricePolicy currentPolicy = oldTicket.getPricePolicy();
                if (currentPolicy == null || currentPolicy.getPricePolicyId() == null) {
                    throw new InvalidTicketStateException("Không xác định được gói hiện tại của vé. Không thể duyệt gia hạn.");
                }
                if (!currentPolicy.getPricePolicyId().equals(policy.getPricePolicyId())) {
                    throw new InvalidTicketStateException("Không thể duyệt: gói gia hạn không trùng với gói hiện tại của vé.");
                }
            } else {
                oldTicket = monthlyTicketRepo
                        .findFirstByVehicleVehiclesIdOrderByEndDateDesc(req.getVehicle().getVehiclesId())
                        .orElse(null);
            }
            
            ParkingCard card = null;
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDate = now;
            LocalDateTime endDate = now.plusMonths(1);
            
            if (oldTicket != null) {
                oldTicket.setStatus(MonthlyTicketStatus.INACTIVE);
                monthlyTicketRepo.save(oldTicket);
                
                card = oldTicket.getParkingCard();
                if (oldTicket.getEndDate().isAfter(now)) {
                    endDate = oldTicket.getEndDate().plusMonths(1);
                }
            } else {
                card = parkingCardRepo.findFirstAvailableMonthlyCard(req.getParkingBranch().getParkingBranchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ giữ xe loại tháng còn trống tại chi nhánh " + req.getParkingBranch().getBranchName()));
            }
            
            card.setStatus(ParkingCardStatus.IN_USE);
            parkingCardRepo.save(card);
            
            MonthlyTicket newTicket = new MonthlyTicket();
            newTicket.setVehicle(req.getVehicle());
            newTicket.setParkingCard(card);
            newTicket.setStartDate(startDate);
            newTicket.setEndDate(endDate);
            newTicket.setStatus(MonthlyTicketStatus.ACTIVE);
            newTicket.setGuestName(req.getUser().getUserFullName());
            newTicket.setGuestPhone(req.getUser().getUserPhone());
            newTicket.setMonthlyTicketRequest(req);
            newTicket.setPricePolicy(req.getPricePolicy());
            
            monthlyTicketRepo.save(newTicket);
        } else if (status == MonthlyTicketRequestStatus.REJECTED) {
            boolean ticketIssuedFromThisRequest = monthlyTicketRepo.existsByMonthlyTicketRequestId(id);

            if (ticketIssuedFromThisRequest) {
                throw new InvalidTicketStateException("Không thể từ chối yêu cầu đã cấp vé.");
            }

            if (req.getStatus() == MonthlyTicketRequestStatus.APPROVED
                    && req.getPayment() != null
                    && req.getPayment().getPaymentStatus() == PaymentStatus.PAID) {
                req.setStatus(MonthlyTicketRequestStatus.PENDING_APPROVAL); 
            }
        }
        
        req.setStatus(status);
        return requestRepo.save(req);
    }
}
