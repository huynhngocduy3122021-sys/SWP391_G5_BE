package Parking.Service;

import Parking.Model.Slot;
import Parking.Repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// Đánh dấu đây là class dịch vụ (Service) thực hiện nghiệp vụ logic của hệ thống quản lý đỗ xe
@Service
// Tự động tạo Constructor tiêm phụ thuộc (Dependency Injection) cho thuộc tính final 'slotRepository' thông qua Lombok
@RequiredArgsConstructor
public class SlotService {

    // Khai báo Repository dùng để truy vấn cơ sở dữ liệu
    private final SlotRepository slotRepository;

    // HÀM LẤY TOÀN BỘ DANH SÁCH SLOT ĐỖ XE
    public List<Slot> getAllSlots() {
        return slotRepository.findAll();
    }

    // HÀM LẤY VỊ TRÍ ĐỖ XE THEO ID
    public Optional<Slot> getSlotById(Long id) {
        return slotRepository.findById(id);
    }

    // HÀM TẠO SLOT MỚI
    public Slot createSlot(Slot slot) {
        return slotRepository.save(slot);
    }

    // HÀM CẬP NHẬT THÔNG TIN SLOT ĐỖ XE THEO ID
    public Slot updateSlot(Long id, Slot newSlot) {
        // Tìm slot cần cập nhật theo ID, nếu không tìm thấy thì quăng ra lỗi (RuntimeException)
        Slot slot = slotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy slot đỗ xe với ID: " + id));

        // Cập nhật các thông tin mới từ client truyền vào
        slot.setSlotCode(newSlot.getSlotCode());
        slot.setVehicleType(newSlot.getVehicleType());
        slot.setAvailable(newSlot.isAvailable());

        // Lưu thông tin đã chỉnh sửa xuống cơ sở dữ liệu
        return slotRepository.save(slot);
    }

    // HÀM XÓA SLOT ĐỖ XE THEO ID
    public void deleteSlot(Long id) {
        slotRepository.deleteById(id);
    }
}