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
import Parking.Repository.ParkingSessionRepository;
import Parking.enums.MonthlyTicketRequestStatus;
import Parking.enums.MonthlyTicketStatus;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import Parking.enums.ParkingSessionStatus;
import Parking.enums.PaymentStatus;
import Parking.dto.request.SubmitMonthlyTicketRequest;
import Parking.dto.response.MonthlyTicketRequestResponse;
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
    private final ParkingSessionRepository parkingSessionRepo;
    private final CurrentUserService currentUserService;
    private final Parking.Repository.UserRepository userRepo;

    private static final List<MonthlyTicketRequestStatus> OPEN_REQUEST_STATUSES = List.of(
            MonthlyTicketRequestStatus.PENDING_PAYMENT,
            MonthlyTicketRequestStatus.PENDING_APPROVAL
    );

    @Transactional
    public MonthlyTicketRequest submitRequest(SubmitMonthlyTicketRequest req) {
        User authenticatedUser = currentUserService.getCurrentUser();
        if (authenticatedUser == null) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng");
        }

        User user = userRepo.findByIdForUpdate(authenticatedUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (requestRepo.existsOpenRequestByUser(
                user.getUserId(), OPEN_REQUEST_STATUSES)) {
            throw new InvalidTicketStateException(
                    "Bạn đang có yêu cầu thẻ tháng chưa hoàn tất. "
                    + "Vui lòng thanh toán hoặc chờ quản lý xét duyệt."
            );
        }

        Vehicle vehicle = vehicleRepo.findById(req.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phương tiện"));

        if (monthlyTicketRepo.existsActiveTicketByVehicle(
                vehicle.getVehiclesId(), LocalDateTime.now())) {
            throw new InvalidTicketStateException(
                    "Phương tiện đã có vé tháng còn hiệu lực. Vui lòng sử dụng chức năng gia hạn."
            );
        }

        PricePolicy policy = policyRepo.findById(req.getPolicyId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ"));
        ParkingBranch branch = branchRepo.findById(req.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh"));

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

    @Transactional(readOnly = true)
    public List<MonthlyTicketRequestResponse> getAllRequests() {
        return requestRepo.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonthlyTicketRequestResponse> getMyRequests() {
        User user = currentUserService.getCurrentUser();
        if (user == null) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng");
        }
        return requestRepo.findByUserUserId(user.getUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public MonthlyTicketRequestResponse toResponse(MonthlyTicketRequest request) {
        Vehicle vehicle = request.getVehicle();
        User user = request.getUser();
        PricePolicy policy = request.getPricePolicy();
        ParkingBranch branch = request.getParkingBranch();
        Payment payment = request.getPayment();
        MonthlyTicket renewalTicket = request.getRenewalOfTicket();

        MonthlyTicketRequestResponse.VehicleSummary vehicleSummary = vehicle == null ? null
                : MonthlyTicketRequestResponse.VehicleSummary.builder()
                    .vehiclesId(vehicle.getVehiclesId())
                    .licensePlate(vehicle.getLicensePlate())
                    .vehicleColor(vehicle.getVehicleColor())
                    .vehicleBrand(vehicle.getVehicleBrand())
                    .vehicleTypeId(vehicle.getVehicleType() == null ? null : vehicle.getVehicleType().getVehicleTypeId())
                    .vehicleTypeName(vehicle.getVehicleType() == null ? null : vehicle.getVehicleType().getTypeName())
                    .build();

        MonthlyTicketRequestResponse.UserSummary userSummary = user == null ? null
                : MonthlyTicketRequestResponse.UserSummary.builder()
                    .userId(user.getUserId())
                    .userFullName(user.getUserFullName())
                    .userEmail(user.getUserEmail())
                    .userPhone(user.getUserPhone())
                    .build();

        MonthlyTicketRequestResponse.PricePolicySummary policySummary = policy == null ? null
                : MonthlyTicketRequestResponse.PricePolicySummary.builder()
                    .pricePolicyId(policy.getPricePolicyId())
                    .policyName(policy.getPolicyName())
                    .basePrice(policy.getBasePrice())
                    .build();

        MonthlyTicketRequestResponse.ParkingBranchSummary branchSummary = branch == null ? null
                : MonthlyTicketRequestResponse.ParkingBranchSummary.builder()
                    .parkingBranchId(branch.getParkingBranchId())
                    .branchName(branch.getBranchName())
                    .build();

        MonthlyTicketRequestResponse.PaymentSummary paymentSummary = payment == null ? null
                : MonthlyTicketRequestResponse.PaymentSummary.builder()
                    .paymentId(payment.getPaymentId())
                    .amount(payment.getAmount())
                    .paymentMethod(payment.getPaymentMethod() == null ? null : payment.getPaymentMethod().name())
                    .paymentStatus(payment.getPaymentStatus() == null ? null : payment.getPaymentStatus().name())
                    .transactionRef(payment.getTransactionRef())
                    .responseCode(payment.getResponseCode())
                    .createdAt(payment.getCreatedAt())
                    .paidAt(payment.getPaidAt())
                    .build();

        MonthlyTicketRequestResponse.RenewalSummary renewalSummary = renewalTicket == null ? null
                : MonthlyTicketRequestResponse.RenewalSummary.builder()
                    .ticketId(renewalTicket.getTicketId())
                    .parkingCardId(renewalTicket.getParkingCard() == null
                            ? null
                            : renewalTicket.getParkingCard().getParkingCardId())
                    .cardCode(renewalTicket.getParkingCard() == null
                            ? null
                            : renewalTicket.getParkingCard().getCardCode())
                    .startDate(renewalTicket.getStartDate())
                    .endDate(renewalTicket.getEndDate())
                    .status(renewalTicket.getStatus() == null ? null : renewalTicket.getStatus().name())
                    .build();

        return MonthlyTicketRequestResponse.builder()
                .id(request.getId())
                .vehicle(vehicleSummary)
                .user(userSummary)
                .pricePolicy(policySummary)
                .parkingBranch(branchSummary)
                .status(request.getStatus() == null ? null : request.getStatus().name())
                .statusCode(request.getStatus() == null ? null : request.getStatus().getCode())
                .createdAt(request.getCreatedAt())
                .payment(paymentSummary)
                .renewalOfTicket(renewalSummary)
                .build();
    }

    @Transactional
    public MonthlyTicketRequest updateStatus(Long id, MonthlyTicketRequestStatus status) {
        if (status == MonthlyTicketRequestStatus.APPROVED) {
            throw new InvalidTicketStateException(
                    "Vui lòng sử dụng API duyệt và chọn thẻ giữ xe còn trống."
            );
        }
        return updateStatusInternal(id, status, null);
    }

    @Transactional
    public MonthlyTicketRequest approveRequest(Long id, Long parkingCardId) {
        if (parkingCardId == null) {
            throw new InvalidTicketStateException("Phải chọn thẻ giữ xe khi duyệt yêu cầu.");
        }
        return updateStatusInternal(id, MonthlyTicketRequestStatus.APPROVED, parkingCardId);
    }

    private MonthlyTicketRequest updateStatusInternal(
            Long id,
            MonthlyTicketRequestStatus status,
            Long parkingCardId) {
        MonthlyTicketRequest req = requestRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu"));
        
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
            }
            
            ParkingCard card = null;
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDate = now;
            LocalDateTime endDate = now.plusMonths(1);
            
            if (oldTicket != null) {
                if (oldTicket.getParkingCard() == null
                        || !oldTicket.getParkingCard().getParkingCardId().equals(parkingCardId)) {
                    throw new InvalidTicketStateException(
                            "Gia hạn phải sử dụng thẻ giữ xe hiện tại của vé."
                    );
                }
                LocalDateTime baseDate = oldTicket.getEndDate().isAfter(now)
                        ? oldTicket.getEndDate()
                        : now;
                oldTicket.setEndDate(baseDate.plusMonths(1));
                oldTicket.setStatus(MonthlyTicketStatus.ACTIVE);
                oldTicket.setPricePolicy(policy);
                monthlyTicketRepo.save(oldTicket);
            } else {
                card = parkingCardRepo.findByParkingCardId(parkingCardId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ giữ xe đã chọn."));

                if (card.getParkingBranch() == null
                        || !card.getParkingBranch().getParkingBranchId()
                                .equals(req.getParkingBranch().getParkingBranchId())) {
                    throw new InvalidTicketStateException(
                            "Thẻ giữ xe đã chọn không thuộc chi nhánh của yêu cầu."
                    );
                }
                if (card.getType() != ParkingCardType.MONTHLY) {
                    throw new InvalidTicketStateException("Chỉ được chọn thẻ loại MONTHLY.");
                }
                if (card.getStatus() != ParkingCardStatus.AVAILABLE) {
                    throw new InvalidTicketStateException("Thẻ giữ xe đã chọn hiện không khả dụng.");
                }
                if (monthlyTicketRepo.existsActiveTicketByCard(card.getParkingCardId(), now)) {
                    throw new InvalidTicketStateException(
                            "Thẻ giữ xe đã được liên kết với một vé tháng còn hiệu lực."
                    );
                }
                if (parkingSessionRepo.existsByParkingCardParkingCardIdAndStatus(
                        card.getParkingCardId(), ParkingSessionStatus.ACTIVE)) {
                    throw new InvalidTicketStateException(
                            "Thẻ giữ xe đang có phiên gửi xe hoạt động."
                    );
                }
                
                // Việc cấp vé chỉ liên kết thẻ với thuê bao. IN_USE chỉ được
                // dùng khi xe thực sự đang có phiên gửi xe hoạt động.
                card.setStatus(ParkingCardStatus.AVAILABLE);
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
            }
        } else if (status == MonthlyTicketRequestStatus.REJECTED) {
            boolean ticketIssuedFromThisRequest = monthlyTicketRepo.existsByMonthlyTicketRequestId(id);

            if (ticketIssuedFromThisRequest) {
                throw new InvalidTicketStateException("Không thể từ chối yêu cầu đã cấp vé.");
            }

            if (req.getStatus() != MonthlyTicketRequestStatus.PENDING_PAYMENT
                    && req.getStatus() != MonthlyTicketRequestStatus.PENDING_APPROVAL) {
                throw new InvalidTicketStateException(
                        "Chỉ có thể từ chối yêu cầu đang chờ thanh toán hoặc chờ duyệt."
                );
            }
        }
        
        req.setStatus(status);
        return requestRepo.save(req);
    }
}
