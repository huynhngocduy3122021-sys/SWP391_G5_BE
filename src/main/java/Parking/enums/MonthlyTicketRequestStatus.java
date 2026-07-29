package Parking.enums;

public enum MonthlyTicketRequestStatus {
    REJECTED(-1),
    PENDING_PAYMENT(0),
    PENDING_APPROVAL(1),
    APPROVED(2),
    EXPIRED(-2);

    private final int code;

    MonthlyTicketRequestStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MonthlyTicketRequestStatus fromCode(int code) {
        for (MonthlyTicketRequestStatus status : MonthlyTicketRequestStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Mã trạng thái yêu cầu vé tháng không hợp lệ: " + code);
    }
}
