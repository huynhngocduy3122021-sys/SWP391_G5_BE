package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Parking.Model.Slot;

// Đăng ký interface này như một Bean Repository trong Spring Container quản lý cơ sở dữ liệu
@Repository
// Kế thừa JpaRepository để thừa hưởng tất cả các hàm CRUD cơ bản (thêm, sửa, xóa, tìm kiếm theo Id,...) cho thực thể Slot với khóa chính kiểu Long
public interface SlotRepository extends JpaRepository<Slot, Long> {
}