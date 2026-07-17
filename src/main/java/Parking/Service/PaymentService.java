package Parking.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

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
import Parking.dto.response.GuestCheckOutResponse;
import Parking.dto.response.VnpayReturnResponse;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import Parking.enums.ParkingSessionStatus;
import Parking.enums.PaymentMethod;
import Parking.enums.PaymentStatus;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

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

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Transactional
    public GuestCheckOutResponse processCheckOutPayment(ParkingSession parkingSession, PaymentMethod paymentMethod, String clientIp, Boolean lostCard, LocalDateTime time) {
        // b1: kiểm tra chưa thanh toán
        boolean paymentExists = paymentRepository.existsByParkingSessionParkingSessionId(parkingSession.getParkingSessionId());
        Payment payment;

        if (paymentExists) {
            Payment existingPayment = paymentRepository.findByParkingSessionParkingSessionId(parkingSession.getParkingSessionId())
                    .orElse(null);
            if (existingPayment != null && existingPayment.getPaymentStatus() == PaymentStatus.PAID) {
                throw new ParkingSessionException("Parking session has already been paid");
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
                    .orElseThrow(() -> new ParkingSessionException("Active price policy not found"));

        LocalDateTime checkOutTime = (time != null) ? time : LocalDateTime.now();
        // b3: tính phí
        BigDecimal parkingFee;
        BigDecimal penaltyFee = (lostCard != null && lostCard) ? new BigDecimal("50000") : BigDecimal.ZERO;
        
        boolean isMonthlyTicketActive = false;
        if (parkingSession.getParkingCard().getType() == ParkingCardType.MONTHLY) {
            isMonthlyTicketActive = monthlyTicketRepository.existsActiveTicketByCard(
                    parkingSession.getParkingCard().getParkingCardId(),
                    checkOutTime
            );
        }

        if (isMonthlyTicketActive || (parkingSession.getParkingCard().getCardCode() != null && parkingSession.getParkingCard().getCardCode().toUpperCase().startsWith("EMP-"))) {
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

        if (request.getStatus() != 0) {
            throw new ParkingSessionException("Yêu cầu này không ở trạng thái chờ thanh toán");
        }

        // Tạo bản ghi Payment
        Payment payment = new Payment();
        payment.setMonthlyTicketRequest(request);
        
        BigDecimal amount = request.getPricePolicy().getBasePrice();
        payment.setAmount(amount);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        
        String txnRef = "TXN_MT_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        payment.setTransactionRef(txnRef);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        
        LocalDateTime expiresAt = LocalDateTime.now(VIETNAM_ZONE).plusMinutes(15);
        payment.setPaymentExpiresAt(expiresAt);

        payment = paymentRepository.save(payment);

        return vnPayService.createPaymentUrl(payment, clientIp);
    }

    public BigDecimal caculateParkingFee(LocalDateTime checkInTime, LocalDateTime checkOutTime, PricePolicy pricePolicy) {
        if (checkInTime == null) {
            throw new ParkingSessionException("Check-in time is missing");
        }

        if (pricePolicy.getBasePrice() == null || pricePolicy.getExtraHourPrice() == null || pricePolicy.getBaseDurationMinutes() == null) {
            throw new ParkingSessionException("Price policy is invalid");
        }

        if (pricePolicy.getBaseDurationMinutes() <= 0) {
            throw new ParkingSessionException("Base duration must be greater than zero");
        }

        if (pricePolicy.getBasePrice().compareTo(BigDecimal.ZERO) < 0 || pricePolicy.getExtraHourPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ParkingSessionException("Parking price cannot be negative");
        }

        long totalMinutes = Duration.between(checkInTime, checkOutTime).toMinutes();
        totalMinutes = Math.max(totalMinutes, 0); // tránh trượng hợp bị null

        BigDecimal fee; 
        if (totalMinutes <= pricePolicy.getBaseDurationMinutes()) {
            fee = pricePolicy.getBasePrice().setScale(2, RoundingMode.HALF_UP);
        } else {
            long extraMinutes = totalMinutes - pricePolicy.getBaseDurationMinutes();
            int extraBlockMinutes = pricePolicy.getExtraDurationMinutes() != null && pricePolicy.getExtraDurationMinutes() > 0 
                    ? pricePolicy.getExtraDurationMinutes() : 60;
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
        String vnpTxnNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");

        Payment payment = paymentRepository.findByTransactionRefForUpdate(txnRef)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thông tin thanh toán: " + txnRef));

        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            return VnpayReturnResponse.builder()
                    .validSignature(true)
                    .success(true)
                    .transactionRef(txnRef)
                    .vnpTransactionNo(payment.getVnpTransactionNo())
                    .responseCode(payment.getResponseCode())
                    .message("Thanh toán đã được xác nhận thành công trước đó")
                    .build();
        }

        boolean isSuccess = "00".equals(responseCode);
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
            if (mtr != null && mtr.getStatus() != null && mtr.getStatus() == 0) {
                mtr.setStatus(1); // PENDING_APPROVAL
                monthlyTicketRequestRepository.save(mtr);
            }
            paymentRepository.save(payment);

            return VnpayReturnResponse.builder()
                    .validSignature(true)
                    .success(true)
                    .transactionRef(txnRef)
                    .vnpTransactionNo(vnpTxnNo)
                    .responseCode(responseCode)
                    .message("Thanh toán thành công. Phiên gửi xe đã kết thúc.")
                    .build();
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            return VnpayReturnResponse.builder()
                    .validSignature(true)
                    .success(false)
                    .transactionRef(txnRef)
                    .vnpTransactionNo(vnpTxnNo)
                    .responseCode(responseCode)
                    .message("Thanh toán thất bại.")
                    .build();
        }
    }

    @Transactional
    public Map<String, String> handleVnPayIpn(Map<String, String> params) {
        Map<String, String> response = new HashMap<>();
        try {
            if (!vnPayService.verifySignature(params)) {
                response.put("RspCode", "97");
                response.put("Message", "Invalid signature");
                return response;
            }

            if (!vnPayService.isCorrectTmnCode(params)) {
                response.put("RspCode", "99");
                response.put("Message", "Incorrect Merchant TMN Code");
                return response;
            }

            String txnRef = params.get("vnp_TxnRef");
            String responseCode = params.get("vnp_ResponseCode");
            String vnpTxnNo = params.get("vnp_TransactionNo");
            String bankCode = params.get("vnp_BankCode");
            String vnpAmountStr = params.get("vnp_Amount");

            Payment payment = paymentRepository.findByTransactionRefForUpdate(txnRef).orElse(null);
            if (payment == null) {
                response.put("RspCode", "01");
                response.put("Message", "Order not found");
                return response;
            }

            BigDecimal expectedAmount = payment.getAmount();
            BigDecimal receivedAmount = vnPayService.convertVnPayAmount(vnpAmountStr);
            if (expectedAmount.compareTo(receivedAmount) != 0) {
                response.put("RspCode", "04");
                response.put("Message", "Invalid amount");
                return response;
            }

            if (payment.getPaymentStatus() == PaymentStatus.PAID || payment.getPaymentStatus() == PaymentStatus.FAILED) {
                response.put("RspCode", "02");
                response.put("Message", "Order already confirmed");
                return response;
            }

            boolean isSuccess = "00".equals(responseCode);
            payment.setVnpTransactionNo(vnpTxnNo);
            payment.setBankCode(bankCode);
            payment.setResponseCode(responseCode);
            
            ParkingSession session = payment.getParkingSession();

            if (isSuccess) {
                payment.setPaymentStatus(PaymentStatus.PAID);
                payment.setPaidAt(LocalDateTime.now());

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
                if (mtr != null && mtr.getStatus() != null && mtr.getStatus() == 0) {
                    mtr.setStatus(1); // PENDING_APPROVAL
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
            response.put("Message", "Unknown error: " + e.getMessage());
        }

        return response;
    }

    @Transactional(readOnly = true)
    public java.util.List<Parking.dto.response.PaymentReportResponse> getAllPaymentsForReport() {
        java.util.List<Payment> payments = paymentRepository.findAll();
        return payments.stream().map(p -> {
            Parking.dto.response.PaymentReportResponse.PaymentReportResponseBuilder builder = Parking.dto.response.PaymentReportResponse.builder()
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
                       .monthlyTicketRequestStatus(mtr.getStatus());
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
                       .sessionBranchName(ps.getParkingBranch() != null ? ps.getParkingBranch().getBranchName() : null);
            }

            return builder.build();
        }).collect(java.util.stream.Collectors.toList());
    }
}
