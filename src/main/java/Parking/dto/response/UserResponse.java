package Parking.dto.response;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class UserResponse {
    
    private Long userId;
    private String userFullName;
    private String userEmail;
    private String userPhone;
    private String userAddress;
    private String userRole;
    private String token;


}
