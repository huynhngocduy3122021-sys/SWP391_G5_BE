package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateParkingBranchRequest {

    @NotBlank(message = "Tên chi nhánh là bắt buộc")
    @Size(max = 255, message = "Tên chi nhánh không được vượt quá 255 ký tự")
    private String branchName;

    @NotBlank(message = "Địa chỉ là bắt buộc")
    @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
    private String address;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phoneNumber;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;
}