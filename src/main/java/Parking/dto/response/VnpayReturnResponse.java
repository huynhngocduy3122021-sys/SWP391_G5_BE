package Parking.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class VnpayReturnResponse {
    private boolean validSignature;
    private boolean success;
    private String transactionRef;
    private String vnpTransactionNo;
    private String responseCode;
    private String message;
    
}
