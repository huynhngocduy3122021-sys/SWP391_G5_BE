package Parking.enums;

public enum MonthlyTicketStatus {
    INACTIVE(0),
    ACTIVE(1);

    private final int code;

    MonthlyTicketStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MonthlyTicketStatus fromCode(int code) {
        for (MonthlyTicketStatus status : MonthlyTicketStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Mã trạng thái vé tháng không hợp lệ: " + code);
    }
}
