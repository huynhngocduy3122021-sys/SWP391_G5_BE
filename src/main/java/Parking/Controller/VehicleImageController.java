package Parking.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import Parking.Model.VehicleImageType;
import Parking.Service.VehicleImageService;
import Parking.dto.response.VehicleImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/parking-session")
@Tag(name = "quản lí phương tiện")
@RequiredArgsConstructor
public class VehicleImageController {
     private final VehicleImageService vehicleImageService;

     @PostMapping(value = "/{parkingSessionId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
     @Operation(summary = "tải ảnh phương tiện")
     public ResponseEntity<List<VehicleImageResponse>> uploadImage(
            @PathVariable Long parkingSessionId,@RequestParam VehicleImageType imageType,@RequestPart("file")List<MultipartFile> files) {
       
                List<VehicleImageResponse> responses = vehicleImageService.uploadVehicleImages(parkingSessionId,imageType,files);

            return ResponseEntity.status(HttpStatus.CREATED).body(responses);
            
    }
    @GetMapping("/{parkingSessionId}/images")
    @Operation(summary = "Lấy danh sách ảnh của phiên gửi xe")
    public ResponseEntity<List<VehicleImageResponse>>
    getImagesBySession(@PathVariable Long parkingSessionId) {
        return ResponseEntity.ok(vehicleImageService.getImagesBySession(parkingSessionId));
    }

}
