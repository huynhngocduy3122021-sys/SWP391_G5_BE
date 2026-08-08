package Parking.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.ParkingCard;
import Parking.Model.ParkingSession;
import Parking.Model.Payment;
import Parking.Model.PricePolicy;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.Repository.PaymentRepository;
import Parking.Repository.PricePolicyRepository;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.MonthlyTicketRequestRepository;
import Parking.Model.MonthlyTicketRequest;
import Parking.Model.IncidentReport;
import Parking.Model.User;
import Parking.Repository.IncidentReportRepository;
import Parking.dto.response.GuestCheckOutResponse;
import Parking.dto.response.PaymentReportResponse;
import Parking.dto.response.VnpayReturnResponse;
import Parking.dto.response.LostCardPaymentResponse;
import Parking.dto.request.CashLostCardPaymentRequest;
import Parking.dto.request.GuestCheckOutRequest;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import Parking.enums.ParkingSessionStatus;
import Parking.enums.PaymentMethod;
import Parking.enums.PaymentStatus;
import Parking.enums.IncidentStatus;
import Parking.enums.IncidentType;
import Parking.enums.UserRole;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

/**
 * Service xử lý toàn bộ logic tính tiền và Thanh toán.
 * Bao gồm:
 * 1. Tính toán số tiền phải trả dựa trên số giờ gửi xe và bảng giá.
 * 2. Tạo link thanh toán VNPay (nếu khách chọn VNPay).
 * 3. Nhận phản hồi từ VNPay để cập nhật trạng thái thanh toán thành CÔNG hoặc
 * THẤT BẠI.
 * 3. Nhận phản hồi từ VNPay để cập nhật trạng thái thanh toán thành CÔNG hoặc THẤT BẠI.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PricePolicyRepository pricePolicyRepository;
    private final VnPayService vnPayService;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingCardRepository parkingCardRepository;
    private final MonthlyTicketRepository monthlyTicketRepository;
    private final MonthlyTicketRequestRepository monthlyTicketRequestRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final BranchScopeService branchScopeService;
    private final CurrentUserService currentUserService;

    private static final BigDecimal LOST_CARD_PAYMENT_AMOUNT = new BigDecimal("50000");

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Nghiệp vụ: Xử lý thanh toán khi khách lấy xe ra.
     * Hàm này sẽ tính toán tổng tiền (gồm tiền gửi xe + tiền phạt nếu có).
     * Nếu chọn VNPay thì sinh ra URL thanh toán. Nếu chọn Tiền mặt thì hoàn thành
     * luôn.
     * Nếu chọn VNPay thì sinh ra URL thanh toán. Nếu chọn Tiền mặt thì hoàn thành luôn.
     */
    @Transactional
    public GuestCheckOutResponse processCheckOutPayment(ParkingSession parkingSession, PaymentMethod paymentMethod,
            String clientIp, Boolean lostCard, LocalDateTime time) {
        // b1: kiểm tra chưa thanh toán
        boolean paymentExists = paymentRepository
                .existsByParkingSessionParkingSessionId(parkingSession.getParkingSessionId());
        Payment payment;

        if (paymentExists) {
            Payment existingPayment = paymentRepository
                    .findByParkingSessionParkingSessionId(parkingSession.getParkingSessionId())
                    .orElse(null);
            if (existingPayment != null && existingPayment.getPaymentStatus() == PaymentStatus.PAID) {
                throw new ParkingSessionException("Phiên gửi xe đã được thanh toán");
            }
            // Tái sử dụng bản ghi cũ chưa thanh toán thành công
            payment = existingPayment;
        } else {
            // Tạo bản ghi mới nếu chưa tồn tại
            payment = new Payment();
            payment.setParkingSession(parkingSession);
        }

        // b2: chính sách tính giá
        Long vehicleTypeId = parkingSession.getVehicle().getVehicleType().getVehicleTypeId();

        PricePolicy pricePolicy = pricePolicyRepository.findFirstActiveHourlyPolicy(vehicleTypeId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy chính sách giá đang hoạt động"));

        LocalDateTime checkOutTime = (time != null) ? time : LocalDateTime.now();
        // b3: tính phí
        BigDecimal parkingFee;
        BigDecimal penaltyFee = (lostCard != null && lostCard) ? new BigDecimal("50000") : BigDecimal.ZERO;

        boolean isMonthlyTicketActive = false;
        if (parkingSession.getParkingCard().getType() == ParkingCardType.MONTHLY) {
            isMonthlyTicketActive = monthlyTicketRepository.existsActiveTicketByCard(
                    parkingSession.getParkingCard().getParkingCardId(),
                    checkOutTime);
        }

        if (isMonthlyTicketActive || (parkingSession.getParkingCard().getCardCode() != null
                && parkingSession.getParkingCard().getCardCode().toUpperCase().startsWith("EMP-"))) {
            parkingFee = BigDecimal.ZERO;
        } else {
            parkingFee = caculateParkingFee(parkingSession.getCheckInTime(), checkOutTime, pricePolicy);
        }

        BigDecimal totalAmount = parkingFee.add(penaltyFee);
        parkingSession.setParkingFee(parkingFee);
        parkingSession.setPenaltyFee(penaltyFee);
        if (lostCard != null && lostCard) {
            parkingSession.getParkingCard().setStatus(ParkingCardStatus.LOST);
        }

        // b4: cập nhật thông tin payment
        payment.setAmount(totalAmount);
        payment.setPaymentMethod(paymentMethod);

        // Tạo transactionRef mới cho mỗi lượt checkout
        String txnRef = "TXN_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        payment.setTransactionRef(txnRef);

        // Reset các thông tin phản hồi từ cổng thanh toán của lượt trước (nếu có)
        payment.setBankCode(null);
        payment.setResponseCode(null);
        payment.setVnpTransactionNo(null);
        payment.setPaidAt(null);
        payment.setPaymentExpiresAt(null);

        GuestCheckOutResponse.GuestCheckOutResponseBuilder responseBuilder = GuestCheckOutResponse.builder()
                .parkingSessionId(parkingSession.getParkingSessionId())
                .amount(totalAmount)
                .paymentMethod(paymentMethod);

        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            payment.setPaymentMethod(PaymentMethod.CASH); // force CASH for free checkout
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setPaidAt(checkOutTime);

            parkingSession.setCheckOutTime(checkOutTime);
            parkingSession.setTotalAmount(totalAmount);
            parkingSession.setStatus(ParkingSessionStatus.COMPLETED);
            parkingSession.setPayment(payment);

            // trả thẻ về AVAILABLE nếu không phải thẻ bị báo mất (LOST)
            ParkingCard parkingCard = parkingSession.getParkingCard();
            if (parkingCard.getStatus() != ParkingCardStatus.LOST) {
                parkingCard.setStatus(ParkingCardStatus.AVAILABLE);
            }

            // Lưu payment trước để tránh lỗi nhân đôi câu lệnh INSERT do hiệu ứng cascade
            payment = paymentRepository.save(payment);
            parkingSessionRepository.save(parkingSession);
            parkingCardRepository.save(parkingCard);

            responseBuilder.paymentId(payment.getPaymentId())
                    .paymentMethod(PaymentMethod.CASH)
                    .paymentStatus(PaymentStatus.PAID)
                    .sessionStatus(ParkingSessionStatus.COMPLETED)
                    .paymentUrl(null)
                    .message("Vé tháng hợp lệ. Miễn phí gửi xe.");
        } else if (paymentMethod == PaymentMethod.CASH) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setPaidAt(checkOutTime);

            parkingSession.setCheckOutTime(checkOutTime);
            parkingSession.setTotalAmount(totalAmount);
            parkingSession.setStatus(ParkingSessionStatus.COMPLETED);
            parkingSession.setPayment(payment);

            // trả thẻ về AVAILABLE nếu không phải thẻ bị báo mất (LOST)
            ParkingCard parkingCard = parkingSession.getParkingCard();
            if (parkingCard.getStatus() != ParkingCardStatus.LOST) {
                parkingCard.setStatus(ParkingCardStatus.AVAILABLE);
            }

            // Lưu payment trước để tránh lỗi nhân đôi câu lệnh INSERT do hiệu ứng cascade
            payment = paymentRepository.save(payment);
            parkingSessionRepository.save(parkingSession);
            parkingCardRepository.save(parkingCard);

            responseBuilder.paymentId(payment.getPaymentId())
                    .paymentStatus(PaymentStatus.PAID)
                    .sessionStatus(ParkingSessionStatus.COMPLETED)
                    .paymentUrl(null)
                    .message("Thanh toán tiền mặt thành công. Phiên gửi xe đã hoàn thành.");
        } else if (paymentMethod == PaymentMethod.VNPAY) {
            payment.setPaymentStatus(PaymentStatus.PENDING);
            LocalDateTime expiresAt = LocalDateTime.now(VIETNAM_ZONE).plusMinutes(15);
            payment.setPaymentExpiresAt(expiresAt);

            parkingSession.setCheckOutTime(checkOutTime);
            parkingSession.setTotalAmount(totalAmount);
            parkingSession.setPayment(payment);

            payment = paymentRepository.save(payment);
            parkingSessionRepository.save(parkingSession);

            String payUrl = vnPayService.createPaymentUrl(payment, clientIp);

            responseBuilder.paymentId(payment.getPaymentId())
                    .paymentStatus(PaymentStatus.PENDING)
                    .sessionStatus(ParkingSessionStatus.ACTIVE)
                    .paymentUrl(payUrl)
                    .message("Vui lòng thực hiện thanh toán qua cổng VNPay để hoàn tất checkout.");
        }

        return responseBuilder.build();
    }

    @Transactional
    public String createMonthlyTicketPayment(Long requestId, String clientIp) {
        MonthlyTicketRequest request = monthlyTicketRequestRepository.findById(requestId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy yêu cầu thẻ tháng"));

        if (request.getStatus() != Parking.enums.MonthlyTicketRequestStatus.PENDING_PAYMENT) {
            throw new ParkingSessionException("Yêu cầu này không ở trạng thái chờ thanh toán");
        }

        // Mỗi yêu cầu thẻ tháng chỉ có duy nhất 1 bản ghi Payment (ràng buộc unique ở
        // DB).
        // Nếu đã tồn tại (ví dụ lần bấm "Thanh toán" trước đó chưa hoàn tất), tái sử
        // dụng
        // và làm mới lại bản ghi đó thay vì insert mới để tránh lỗi trùng khóa.
        Payment payment = paymentRepository.findByMonthlyTicketRequestId(requestId)
                .orElseGet(Payment::new);
        payment.setMonthlyTicketRequest(request);

        BigDecimal amount = request.getPricePolicy().getBasePrice();
        payment.setAmount(amount);
        payment.setPaymentMethod(PaymentMethod.VNPAY);

        String txnRef = "TXN_MT_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        payment.setTransactionRef(txnRef);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setResponseCode(null);
        payment.setPaidAt(null);

        LocalDateTime expiresAt = LocalDateTime.now(VIETNAM_ZONE).plusMinutes(15);
        payment.setPaymentExpiresAt(expiresAt);

        payment = paymentRepository.save(payment);

        return vnPayService.createPaymentUrl(payment, clientIp);
    }

    /**
     * Tạo hoặc tái sử dụng payment phí mất thẻ sau khi staff đã tiếp nhận incident.
     */
    @Transactional
    public LostCardPaymentResponse createLostCardPayment(Long incidentId, String clientIp) {
        User currentUser = currentUserService.getCurrentUser();
        IncidentReport incident = incidentReportRepository.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo mất thẻ"));

        if (incident.getIncidentType() != IncidentType.LOST_CARD) {
            throw new ParkingSessionException("Incident này không phải báo mất thẻ");
        }
        if (currentUser.getUserRole() != UserRole.USER
                || incident.getReporter() == null
                || !currentUser.getUserId().equals(incident.getReporter().getUserId())) {
            throw new ParkingSessionException("Chỉ user tạo báo cáo mới được thanh toán báo cáo này");
        }
        if (incident.getStatus() != IncidentStatus.PENDING
                && incident.getStatus() != IncidentStatus.IN_PROGRESS
                && incident.getStatus() != IncidentStatus.WAITING_PAYMENT) {
            throw new ParkingSessionException("Báo mất thẻ đã đóng hoặc không còn nhận thanh toán");
        }
        if (incident.getReporter() == null || incident.getParkingCard() == null) {
            throw new ParkingSessionException("Báo mất thẻ thiếu thông tin chủ thẻ hoặc thẻ xe");
        }
        if (incident.getParkingCard().getType() != ParkingCardType.MONTHLY) {
            throw new ParkingSessionException("Thẻ khách/thẻ lượt không sử dụng phí thay thẻ tháng");
        }

        if (incident.getParkingSession() != null
                && incident.getParkingSession().getStatus() == ParkingSessionStatus.ACTIVE) {
            throw new ParkingSessionException(
                    "Xe còn trong bãi. Vui lòng checkout bằng thẻ guest trước khi thanh toán phí mất thẻ");
        }

        boolean owner = monthlyTicketRepository.findActiveTicketsForLostCard(
                        incident.getParkingCard().getParkingCardId(), LocalDateTime.now())
                .stream()
                .anyMatch(ticket -> ticket.getVehicle() != null
                        && ticket.getVehicle().getUser() != null
                        && incident.getReporter().getUserId().equals(
                                ticket.getVehicle().getUser().getUserId()));
        if (!owner) {
            throw new ParkingSessionException("Chủ báo cáo không còn sở hữu vé tháng của thẻ này");
        }

        Payment payment = paymentRepository.findByIncidentReportIncidentId(incidentId)
                .orElse(null);
        if (payment != null && payment.getPaymentStatus() == PaymentStatus.PAID) {
            return buildLostCardPaymentResponse(incident, payment, null);
        }
        if (payment != null && payment.getPaymentStatus() == PaymentStatus.CASH_PENDING_VERIFICATION) {
            throw new ParkingSessionException("Báo cáo đã chọn thanh toán tiền mặt, không thể tạo giao dịch VNPay");
        }
        if (payment != null && payment.getPaymentStatus() == PaymentStatus.PENDING
                && payment.getPaymentMethod() == PaymentMethod.VNPAY) {
            String paymentUrl = vnPayService.createPaymentUrl(payment, clientIp);
            return buildLostCardPaymentResponse(incident, payment, paymentUrl);
        }
        if (payment == null) {
            payment = new Payment();
        }

        payment.setIncidentReport(incident);
        payment.setAmount(LOST_CARD_PAYMENT_AMOUNT);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setTransactionRef("TXN_LOST_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8));
        payment.setResponseCode(null);
        payment.setVnpTransactionNo(null);
        payment.setBankCode(null);
        payment.setPaidAt(null);
        payment.setPaymentExpiresAt(LocalDateTime.now(VIETNAM_ZONE).plusMinutes(15));

        payment = paymentRepository.save(payment);
        incident.setPayment(payment);
        incident.setStatus(IncidentStatus.WAITING_PAYMENT);
        incident.setUpdatedAt(LocalDateTime.now());
        incidentReportRepository.save(incident);

        String paymentUrl = vnPayService.createPaymentUrl(payment, clientIp);
        return buildLostCardPaymentResponse(incident, payment, paymentUrl);
    }

    /**
     * Checkout xe đang trong bãi bằng thẻ guest và tạo đúng một giao dịch VNPay.
     * Giao dịch bao gồm phí gửi xe (nếu có) và phí mất thẻ 50.000 VND; sau callback
     * VNPay thành công, payment sẽ chuyển PAID và incident chuyển IN_PROGRESS.
     */
    @Transactional
    public GuestCheckOutResponse createLostCardCheckoutPayment(
            Long incidentId, GuestCheckOutRequest request, String clientIp) {
        User currentUser = currentUserService.getCurrentUser();
        IncidentReport incident = incidentReportRepository.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo mất thẻ"));

        if (incident.getIncidentType() != IncidentType.LOST_CARD
                || incident.getParkingCard() == null
                || incident.getParkingCard().getType() != ParkingCardType.MONTHLY) {
            throw new ParkingSessionException("Chỉ thẻ tháng mới sử dụng được luồng checkout mất thẻ");
        }
        if (incident.getReporter() == null
                || !currentUser.getUserId().equals(incident.getReporter().getUserId())) {
            throw new ParkingSessionException("Chỉ chủ thẻ mới được checkout và thanh toán báo cáo này");
        }
        if (incident.getStatus() != IncidentStatus.PENDING
                && incident.getStatus() != IncidentStatus.IN_PROGRESS
                && incident.getStatus() != IncidentStatus.WAITING_PAYMENT) {
            throw new ParkingSessionException("Báo cáo mất thẻ đã đóng hoặc không còn nhận thanh toán");
        }
        if (request.getPaymentMethod() != null && request.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new ParkingSessionException("Luồng mất thẻ chỉ hỗ trợ thanh toán VNPay");
        }

        ParkingCard guestCard = parkingCardRepository.findByCardCodeIgnoreCase(request.getCardCode().trim())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ guest"));
        if (guestCard.getType() != ParkingCardType.REGULAR
                || guestCard.getStatus() != ParkingCardStatus.AVAILABLE) {
            throw new ParkingSessionException("Thẻ sử dụng checkout phải là thẻ guest đang khả dụng");
        }
        if (guestCard.getParkingBranch() == null
                || !guestCard.getParkingBranch().getParkingBranchId()
                        .equals(incident.getParkingBranch().getParkingBranchId())) {
            throw new ParkingSessionException("Thẻ guest không thuộc chi nhánh của báo cáo");
        }

        ParkingSession session = incident.getParkingSession();
        if (session == null || session.getStatus() != ParkingSessionStatus.ACTIVE) {
            throw new ParkingSessionException("Không còn phiên gửi xe hoạt động cần checkout");
        }
        if (request.getLicensePlate() == null
                || !session.getVehicle().getLicensePlate().equalsIgnoreCase(request.getLicensePlate().trim())) {
            throw new ParkingSessionException("Biển số xe checkout không khớp báo cáo mất thẻ");
        }

        GuestCheckOutResponse response = processCheckOutPayment(
                session, PaymentMethod.VNPAY, clientIp, true, request.getTime());

        Payment payment = paymentRepository.findByParkingSessionParkingSessionId(session.getParkingSessionId())
                .orElseThrow(() -> new ParkingSessionException("Không tạo được giao dịch checkout mất thẻ"));
        payment.setIncidentReport(incident);
        incident.setPayment(payment);
        incident.setLostCardFee(LOST_CARD_PAYMENT_AMOUNT);
        incident.setStatus(IncidentStatus.WAITING_PAYMENT);
        incident.setUpdatedAt(LocalDateTime.now(VIETNAM_ZONE));
        paymentRepository.save(payment);
        incidentReportRepository.save(incident);

        return response;
    }

    /** Staff/Manager/Admin ghi nhận đã thu 50.000 VND tiền mặt tại quầy. */
    @Transactional
    public LostCardPaymentResponse createLostCardCashPayment(
            Long incidentId, CashLostCardPaymentRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getUserRole() != UserRole.STAFF
                && currentUser.getUserRole() != UserRole.MANAGER
                && currentUser.getUserRole() != UserRole.ADMIN) {
            throw new ParkingSessionException("Chỉ staff, manager hoặc admin được ghi nhận thanh toán tiền mặt");
        }

        IncidentReport incident = incidentReportRepository.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo mất thẻ"));
        validateLostCardPaymentIncident(incident);
        branchScopeService.assertSameBranch(incident.getParkingBranch().getParkingBranchId());

        Payment payment = paymentRepository.findByIncidentReportIncidentId(incidentId).orElse(null);
        if (payment != null && payment.getPaymentStatus() == PaymentStatus.PAID) {
            return buildLostCardPaymentResponse(incident, payment, null);
        }
        if (payment != null && (payment.getPaymentStatus() == PaymentStatus.PENDING
                || payment.getPaymentStatus() == PaymentStatus.CASH_PENDING_VERIFICATION)) {
            if (payment.getPaymentMethod() != PaymentMethod.CASH) {
                throw new ParkingSessionException("Báo cáo đã chọn thanh toán VNPay, không thể chọn tiền mặt");
            }
            markCashPaymentPaid(payment, incident, currentUser);
            return buildLostCardPaymentResponse(incident, payment, null);
        }
        if (payment == null) {
            payment = new Payment();
        }

        payment.setIncidentReport(incident);
        payment.setAmount(LOST_CARD_PAYMENT_AMOUNT);
        payment.setPaymentMethod(PaymentMethod.CASH);
        // Staff/manager là người thu và xác nhận tại quầy, nên tiền mặt được PAID ngay.
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setTransactionRef("CASH_LOST_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8));
        payment.setResponseCode(null);
        payment.setVnpTransactionNo(null);
        payment.setBankCode(null);
        payment.setPaidAt(LocalDateTime.now(VIETNAM_ZONE));
        payment.setPaymentExpiresAt(null);
        payment.setCashReceiptNumber(request.getReceiptNumber().trim());
        payment.setCashNote(request.getNote());
        payment.setCashCollectedBy(currentUser);
        payment.setCashCollectedAt(LocalDateTime.now(VIETNAM_ZONE));
        payment.setCashVerifiedBy(currentUser);
        payment.setCashVerifiedAt(LocalDateTime.now(VIETNAM_ZONE));

        payment = paymentRepository.save(payment);
        incident.setPayment(payment);
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setUpdatedAt(LocalDateTime.now(VIETNAM_ZONE));
        incidentReportRepository.save(incident);
        return buildLostCardPaymentResponse(incident, payment, null);
    }

    private void markCashPaymentPaid(Payment payment, IncidentReport incident, User operator) {
        payment.setAmount(LOST_CARD_PAYMENT_AMOUNT);
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now(VIETNAM_ZONE));
        payment.setCashVerifiedBy(operator);
        payment.setCashVerifiedAt(LocalDateTime.now(VIETNAM_ZONE));
        paymentRepository.save(payment);

        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setUpdatedAt(LocalDateTime.now(VIETNAM_ZONE));
        incidentReportRepository.save(incident);
    }

    /** Manager xác nhận tiền mặt; chỉ sau bước này mới được cấp thẻ mới. */
    @Transactional
    public LostCardPaymentResponse verifyLostCardCashPayment(Long incidentId) {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getUserRole() != UserRole.MANAGER && currentUser.getUserRole() != UserRole.ADMIN) {
            throw new ParkingSessionException("Chỉ manager hoặc admin được xác nhận tiền mặt");
        }

        IncidentReport incident = incidentReportRepository.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo mất thẻ"));
        validateLostCardPaymentIncident(incident);
        branchScopeService.assertSameBranch(incident.getParkingBranch().getParkingBranchId());

        Payment payment = paymentRepository.findByIncidentReportIncidentId(incidentId)
                .orElseThrow(() -> new ParkingSessionException("Chưa có khoản tiền mặt cần xác nhận"));
        if (payment.getPaymentMethod() != PaymentMethod.CASH) {
            throw new ParkingSessionException("Khoản thanh toán của báo cáo không phải tiền mặt");
        }
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            return buildLostCardPaymentResponse(incident, payment, null);
        }
        if (payment.getPaymentStatus() != PaymentStatus.CASH_PENDING_VERIFICATION) {
            throw new ParkingSessionException("Khoản tiền mặt không ở trạng thái chờ manager xác nhận");
        }

        payment.setAmount(LOST_CARD_PAYMENT_AMOUNT);
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now(VIETNAM_ZONE));
        payment.setCashVerifiedBy(currentUser);
        payment.setCashVerifiedAt(LocalDateTime.now(VIETNAM_ZONE));
        paymentRepository.save(payment);

        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setUpdatedAt(LocalDateTime.now(VIETNAM_ZONE));
        incidentReportRepository.save(incident);
        return buildLostCardPaymentResponse(incident, payment, null);
    }

    private void validateLostCardPaymentIncident(IncidentReport incident) {
        if (incident.getIncidentType() != IncidentType.LOST_CARD) {
            throw new ParkingSessionException("Incident này không phải báo mất thẻ");
        }
        if (incident.getStatus() != IncidentStatus.PENDING
                && incident.getStatus() != IncidentStatus.IN_PROGRESS
                && incident.getStatus() != IncidentStatus.WAITING_PAYMENT) {
            throw new ParkingSessionException("Báo mất thẻ đã đóng hoặc không còn nhận thanh toán");
        }
        if (incident.getReporter() == null || incident.getParkingCard() == null) {
            throw new ParkingSessionException("Báo mất thẻ thiếu thông tin chủ thẻ hoặc thẻ xe");
        }
    }

    private LostCardPaymentResponse buildLostCardPaymentResponse(
            IncidentReport incident, Payment payment, String paymentUrl) {
        return LostCardPaymentResponse.builder()
                .incidentId(incident.getIncidentId())
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .transactionRef(payment.getTransactionRef())
                .paymentUrl(paymentUrl)
                .cashReceiptNumber(payment.getCashReceiptNumber())
                .cashCollectedByUserId(payment.getCashCollectedBy() != null
                        ? payment.getCashCollectedBy().getUserId() : null)
                .cashCollectedAt(payment.getCashCollectedAt())
                .cashVerifiedByUserId(payment.getCashVerifiedBy() != null
                        ? payment.getCashVerifiedBy().getUserId() : null)
                .cashVerifiedAt(payment.getCashVerifiedAt())
                .build();
    }

    private void markLostCardPaymentPaid(Payment payment) {
        IncidentReport incident = payment.getIncidentReport();
        if (incident != null && incident.getIncidentType() == IncidentType.LOST_CARD
                && incident.getStatus() == IncidentStatus.WAITING_PAYMENT) {
            incident.setStatus(IncidentStatus.IN_PROGRESS);
            incident.setUpdatedAt(LocalDateTime.now());
            incidentReportRepository.save(incident);
        }
    }

    public BigDecimal caculateParkingFee(LocalDateTime checkInTime, LocalDateTime checkOutTime,
            PricePolicy pricePolicy) {
        if (checkInTime == null) {
            throw new ParkingSessionException("Thiếu thời gian xe vào");
        }

        if (pricePolicy.getBasePrice() == null || pricePolicy.getExtraHourPrice() == null
                || pricePolicy.getBaseDurationMinutes() == null) {
            throw new ParkingSessionException("Chính sách giá không hợp lệ");
        }

        if (pricePolicy.getBaseDurationMinutes() <= 0) {
            throw new ParkingSessionException("Thời lượng cơ bản phải lớn hơn 0");
        }

        if (pricePolicy.getBasePrice().compareTo(BigDecimal.ZERO) < 0
                || pricePolicy.getExtraHourPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ParkingSessionException("Phí gửi xe không được là số âm");
        }

        long totalMinutes = Duration.between(checkInTime, checkOutTime).toMinutes();
        totalMinutes = Math.max(totalMinutes, 0); // tránh trượng hợp bị null

        BigDecimal fee;
        if (totalMinutes <= pricePolicy.getBaseDurationMinutes()) {
            fee = pricePolicy.getBasePrice().setScale(2, RoundingMode.HALF_UP);
        } else {
            long extraMinutes = totalMinutes - pricePolicy.getBaseDurationMinutes();
            int extraBlockMinutes = pricePolicy.getExtraDurationMinutes() != null
                    && pricePolicy.getExtraDurationMinutes() > 0
                            ? pricePolicy.getExtraDurationMinutes()
                            : 60;
            long extraBlocks = (long) Math.ceil(extraMinutes / (double) extraBlockMinutes);
            BigDecimal extraAmount = pricePolicy.getExtraHourPrice().multiply(BigDecimal.valueOf(extraBlocks));
            fee = pricePolicy.getBasePrice().add(extraAmount).setScale(2, RoundingMode.HALF_UP);
        }

        // Đảm bảo mức phí tối thiểu cho một phiên gửi xe là 10,000 VND
        if (fee.compareTo(BigDecimal.valueOf(10000)) < 0) {
            return BigDecimal.valueOf(10000);
        }
        return fee;
    }

    /**
     * Hàm này được gọi khi khách hàng thanh toán xong trên web VNPay và bị chuyển
     * hướng (Redirect) trả về Web của chúng ta.
     * Nhiệm vụ là đọc các tham số VNPay gửi kèm trên URL để xem thanh toán thành
     * công hay chưa.
     * Hàm này được gọi khi khách hàng thanh toán xong trên web VNPay và bị chuyển hướng (Redirect) trả về Web của chúng ta.
     * Nhiệm vụ là đọc các tham số VNPay gửi kèm trên URL để xem thanh toán thành công hay chưa.
     */
    @Transactional
    public VnpayReturnResponse handleVnPayCallback(Map<String, String> params) {
        boolean isValidSig = vnPayService.verifySignature(params);
        if (!isValidSig) {
            return VnpayReturnResponse.builder()
                    .validSignature(false)
                    .success(false)
                    .message("Chữ ký không hợp lệ")
                    .build();
        }

        if (!vnPayService.isCorrectTmnCode(params)) {
            return VnpayReturnResponse.builder()
                    .validSignature(true)
                    .success(false)
                    .message("Mã TMN không khớp")
                    .build();
        }

        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String vnpTxnNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");

        Payment payment = paymentRepository.findByTransactionRefForUpdate(txnRef)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thông tin thanh toán: " + txnRef));

        ParkingSession parkingSession = payment.getParkingSession();
        MonthlyTicketRequest monthlyRequest = payment.getMonthlyTicketRequest();
        IncidentReport lostCardIncident = payment.getIncidentReport();

        String paymentType;
        if (parkingSession != null) {
            paymentType = "PARKING_SESSION";
        } else if (monthlyRequest != null) {
            paymentType = "MONTHLY_TICKET";
        } else if (lostCardIncident != null
                && lostCardIncident.getIncidentType() == IncidentType.LOST_CARD) {
            paymentType = "LOST_CARD";
        } else {
            throw new ParkingSessionException("Giao dịch không gắn với đối tượng thanh toán");
        }

        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            return buildVnPayReturnResponse(payment, paymentType, true,
                    "Thanh toán đã được xác nhận thành công trước đó", txnRef, payment.getVnpTransactionNo(),
                    payment.getResponseCode());
        }
        if (payment.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return buildVnPayReturnResponse(payment, paymentType, false,
                    "Báo cáo mất thẻ đã bị hủy, giao dịch này không còn hiệu lực.",
                    txnRef, vnpTxnNo, responseCode);
        }

        boolean isSuccess = "00".equals(responseCode)
                && (transactionStatus == null || "00".equals(transactionStatus));
        if (isSuccess) {
            String vnpAmountStr = params.get("vnp_Amount");
            if (vnpAmountStr != null) {
                BigDecimal expectedAmount = payment.getAmount();
                BigDecimal receivedAmount = vnPayService.convertVnPayAmount(vnpAmountStr);
                if (expectedAmount.compareTo(receivedAmount) != 0) {
                    isSuccess = false;
                    responseCode = "04";
                }
            }
        }
        payment.setVnpTransactionNo(vnpTxnNo);
        payment.setBankCode(bankCode);
        payment.setResponseCode(responseCode);

        ParkingSession session = payment.getParkingSession();

        if (isSuccess) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());

            markLostCardPaymentPaid(payment);

            if (session != null) {
                session.setStatus(ParkingSessionStatus.COMPLETED);

                ParkingCard card = session.getParkingCard();
                if (card != null && card.getStatus() != ParkingCardStatus.LOST) {
                    card.setStatus(ParkingCardStatus.AVAILABLE);
                    parkingCardRepository.save(card);
                }
                parkingSessionRepository.save(session);
            }

            MonthlyTicketRequest mtr = payment.getMonthlyTicketRequest();
            if (mtr != null && mtr.getStatus() != null
                    && mtr.getStatus() == Parking.enums.MonthlyTicketRequestStatus.PENDING_PAYMENT) {
                mtr.setStatus(Parking.enums.MonthlyTicketRequestStatus.PENDING_APPROVAL); // PENDING_APPROVAL
                monthlyTicketRequestRepository.save(mtr);
            }
            paymentRepository.save(payment);

            String successMessage = "LOST_CARD".equals(paymentType)
                    ? "Thanh toán phí mất thẻ thành công."
                    : "Thanh toán thành công. Phiên gửi xe đã kết thúc.";
            return buildVnPayReturnResponse(payment, paymentType, true,
                    successMessage, txnRef, vnpTxnNo, responseCode);
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            return buildVnPayReturnResponse(payment, paymentType, false,
                    "Thanh toán VNPay thất bại (mã " + responseCode + ").",
                    txnRef, vnpTxnNo, responseCode);
        }
    }

    private VnpayReturnResponse buildVnPayReturnResponse(Payment payment, String paymentType, boolean isSuccess,
            String message, String txnRef, String vnpTxnNo, String responseCode) {
        VnpayReturnResponse.VnpayReturnResponseBuilder responseBuilder = VnpayReturnResponse.builder()
                .validSignature(true)
                .success(isSuccess)
                .transactionRef(txnRef)
                .vnpTransactionNo(vnpTxnNo)
                .responseCode(responseCode)
                .paymentType(paymentType)
                .paymentStatus(payment.getPaymentStatus());

        MonthlyTicketRequest monthlyRequest = payment.getMonthlyTicketRequest();
        if (monthlyRequest != null) {
            responseBuilder.requestId(monthlyRequest.getId());

            if (monthlyRequest.getVehicle() != null) {
                responseBuilder.vehicleId(monthlyRequest.getVehicle().getVehiclesId());
                responseBuilder.licensePlate(monthlyRequest.getVehicle().getLicensePlate());
            }

            if (monthlyRequest.getPricePolicy() != null) {
                responseBuilder.policyId(monthlyRequest.getPricePolicy().getPricePolicyId());
                responseBuilder.policyName(monthlyRequest.getPricePolicy().getPolicyName());
            }
        }

        IncidentReport lostCardIncident = payment.getIncidentReport();
        if (lostCardIncident != null && "LOST_CARD".equals(paymentType)) {
            responseBuilder.incidentId(lostCardIncident.getIncidentId());
        }

        return responseBuilder
                .message(message)
                .build();
    }

    @Transactional
    public Map<String, String> handleVnPayIpn(Map<String, String> params) {
        Map<String, String> response = new HashMap<>();
        try {
            if (!vnPayService.verifySignature(params)) {
                response.put("RspCode", "97");
                response.put("Message", "Chữ ký không hợp lệ");
                return response;
            }

            if (!vnPayService.isCorrectTmnCode(params)) {
                response.put("RspCode", "99");
                response.put("Message", "Incorrect Merchant TMN Code");
                return response;
            }

            String txnRef = params.get("vnp_TxnRef");
            String responseCode = params.get("vnp_ResponseCode");
            String transactionStatus = params.get("vnp_TransactionStatus");
            String vnpTxnNo = params.get("vnp_TransactionNo");
            String bankCode = params.get("vnp_BankCode");
            String vnpAmountStr = params.get("vnp_Amount");

            Payment payment = paymentRepository.findByTransactionRefForUpdate(txnRef).orElse(null);
            if (payment == null) {
                response.put("RspCode", "01");
                response.put("Message", "Không tìm thấy giao dịch");
                return response;
            }

            BigDecimal expectedAmount = payment.getAmount();
            BigDecimal receivedAmount = vnPayService.convertVnPayAmount(vnpAmountStr);
            if (expectedAmount.compareTo(receivedAmount) != 0) {
                response.put("RspCode", "04");
                response.put("Message", "Số tiền không hợp lệ");
                return response;
            }

            if (payment.getPaymentStatus() == PaymentStatus.PAID
                    || payment.getPaymentStatus() == PaymentStatus.FAILED
                    || payment.getPaymentStatus() == PaymentStatus.CANCELLED) {
                response.put("RspCode", "02");
                response.put("Message", "Giao dịch đã được xác nhận");
                return response;
            }

            boolean isSuccess = "00".equals(responseCode)
                    && (transactionStatus == null || "00".equals(transactionStatus));
            payment.setVnpTransactionNo(vnpTxnNo);
            payment.setBankCode(bankCode);
            payment.setResponseCode(responseCode);

            ParkingSession session = payment.getParkingSession();

            if (isSuccess) {
                payment.setPaymentStatus(PaymentStatus.PAID);
                payment.setPaidAt(LocalDateTime.now());

                markLostCardPaymentPaid(payment);

                if (session != null) {
                    session.setStatus(ParkingSessionStatus.COMPLETED);
                    ParkingCard card = session.getParkingCard();
                    if (card != null && card.getStatus() != ParkingCardStatus.LOST) {
                        card.setStatus(ParkingCardStatus.AVAILABLE);
                        parkingCardRepository.save(card);
                    }
                    parkingSessionRepository.save(session);
                }

                MonthlyTicketRequest mtr = payment.getMonthlyTicketRequest();
                if (mtr != null && mtr.getStatus() != null
                        && mtr.getStatus() == Parking.enums.MonthlyTicketRequestStatus.PENDING_PAYMENT) {
                    mtr.setStatus(Parking.enums.MonthlyTicketRequestStatus.PENDING_APPROVAL); // PENDING_APPROVAL
                    monthlyTicketRequestRepository.save(mtr);
                }
            } else {
                payment.setPaymentStatus(PaymentStatus.FAILED);
            }
            paymentRepository.save(payment);

            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");

        } catch (Exception e) {
            response.put("RspCode", "99");
            response.put("Message", "Lỗi không xác định: " + e.getMessage());
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<PaymentReportResponse> getAllPaymentsForReport() {
        User currentUser = currentUserService.getCurrentUser();
        Stream<Payment> paymentStream = paymentRepository.findAll().stream();

        if (currentUser.getUserRole() == UserRole.STAFF) {
            if (currentUser.getParkingBranch() == null) {
                throw new ParkingSessionException("Tài khoản chưa được gán chi nhánh");
            }

            Long staffBranchId = currentUser.getParkingBranch().getParkingBranchId();
            paymentStream = paymentStream.filter(payment -> {
                ParkingSession session = payment.getParkingSession();
                return session != null
                        && payment.getMonthlyTicketRequest() == null
                        && session.getParkingBranch() != null
                        && staffBranchId.equals(session.getParkingBranch().getParkingBranchId());
            });
        }

        List<Payment> payments = paymentStream.toList();
        return payments.stream().map(p -> {
            PaymentReportResponse.PaymentReportResponseBuilder builder = PaymentReportResponse
                    .builder()
                    .paymentId(p.getPaymentId())
                    .amount(p.getAmount())
                    .paymentMethod(p.getPaymentMethod() != null ? p.getPaymentMethod().name() : null)
                    .paymentStatus(p.getPaymentStatus() != null ? p.getPaymentStatus().name() : null)
                    .createdAt(p.getCreatedAt())
                    .paidAt(p.getPaidAt())
                    .transactionRef(p.getTransactionRef());

            // Monthly ticket request info
            MonthlyTicketRequest mtr = p.getMonthlyTicketRequest();
            if (mtr != null) {
                builder.monthlyTicketRequestId(mtr.getId())
                        .monthlyTicketRequestStatus(mtr.getStatus() != null ? mtr.getStatus().getCode() : null);
                if (mtr.getPricePolicy() != null) {
                    builder.policyName(mtr.getPricePolicy().getPolicyName())
                            .policyBasePrice(mtr.getPricePolicy().getBasePrice());
                }
                if (mtr.getParkingBranch() != null) {
                    builder.branchName(mtr.getParkingBranch().getBranchName())
                            .branchId(mtr.getParkingBranch().getParkingBranchId());
                }
                if (mtr.getVehicle() != null) {
                    builder.vehicleLicensePlate(mtr.getVehicle().getLicensePlate());
                }
                if (mtr.getUser() != null) {
                    builder.userName(mtr.getUser().getUserFullName());
                }
            }

            // Parking session info
            ParkingSession ps = p.getParkingSession();
            if (ps != null) {
                builder.parkingSessionId(ps.getParkingSessionId())
                        .sessionBranchName(
                                ps.getParkingBranch() != null ? ps.getParkingBranch().getBranchName() : null);
            }

            return builder.build();
        }).toList();
    }
}
