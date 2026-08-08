package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CashLostCardPaymentRequest {

    @NotBlank(message = "Số biên lai tiền mặt là bắt buộc")
    @Size(max = 100, message = "Số biên lai không được vượt quá 100 ký tự")
    private String receiptNumber;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}
