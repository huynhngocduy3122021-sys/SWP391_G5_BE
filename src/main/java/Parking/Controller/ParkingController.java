package Parking.Controller;

import Parking.Model.Slot;
import Parking.Service.SlotService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Khai báo đây là một RestController để định nghĩa các API trả về dữ liệu kiểu JSON/XML
@RestController
// Định nghĩa đường dẫn gốc cho các endpoint API của Controller này
@RequestMapping("/api/parking")
// Cho phép gọi API chéo nguồn (CORS) từ bất kỳ tên miền/cổng nào khác (để frontend gọi được API)
@CrossOrigin("*")
// Tự động tạo Constructor tiêm phụ thuộc (Dependency Injection) cho thuộc tính final 'slotService' thông qua Lombok
@RequiredArgsConstructor

public class ParkingController {

    // Khai báo lớp Service chứa logic xử lý
    private final SlotService slotService;

    // API LẤY TOÀN BỘ DANH SÁCH SLOT ĐỖ XE
    // GET: http://localhost:8080/api/parking/slots
    @GetMapping("/slots")
    public ResponseEntity<List<Slot>> getAllSlots() {
        return ResponseEntity.ok(slotService.getAllSlots());
    }

    // API LẤY SLOT ĐỖ XE THEO ID
    // GET: http://localhost:8080/api/parking/slots/{id}
    @GetMapping("/slots/{id}")
    public ResponseEntity<Slot> getSlotById(@PathVariable Long id) {
        return slotService.getSlotById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // API TẠO MỚI SLOT ĐỖ XE
    // POST: http://localhost:8080/api/parking/slots
    @PostMapping("/slots")
    public ResponseEntity<Slot> createSlot(@RequestBody Slot slot) {
        Slot createdSlot = slotService.createSlot(slot);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSlot);
    }

    // API CẬP NHẬT SLOT ĐỖ XE THEO ID
    // PUT: http://localhost:8080/api/parking/slots/{id}
    @PutMapping("/slots/{id}")
    public ResponseEntity<Slot> updateSlot(@PathVariable Long id, @RequestBody Slot slot) {
        try {
            return ResponseEntity.ok(slotService.updateSlot(id, slot));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // Trả về lỗi 404 nếu không thấy slot
        }
    }

    // API XÓA SLOT ĐỖ XE THEO ID
    // DELETE: http://localhost:8080/api/parking/slots/{id}
    @DeleteMapping("/slots/{id}")
    public ResponseEntity<String> deleteSlot(@PathVariable Long id) {
        try {
            slotService.deleteSlot(id);
            return ResponseEntity.ok("Deleted Successfully"); // Trả về thông báo thành công
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting slot"); // Lỗi 500 nếu gặp vấn đề lúc xóa
        }
    }
}