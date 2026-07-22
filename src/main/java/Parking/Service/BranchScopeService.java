package Parking.Service;

import Parking.Model.User;
import Parking.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BranchScopeService {

    @Autowired
    private CurrentUserService currentUserService;

    // Giới hạn dữ liệu theo chi nhánh. MANAGER/STAFF chỉ được phép truy cập dữ liệu thuộc chi nhánh mình quản lý. ADMIN được quyền xem toàn bộ bãi xe.
    public Long resolveReadableBranchId(Long requestedBranchId) {
        User user = currentUserService.getCurrentUser();

        if (user.getUserRole() == UserRole.ADMIN) {
            return requestedBranchId; // null = xem tất cả
        }

        if (user.getUserRole() == UserRole.MANAGER || user.getUserRole() == UserRole.STAFF) {
            Long userBranchId = requireUserBranchId(user);

            // Ngăn chặn MANAGER/STAFF đọc dữ liệu của chi nhánh khác
            if (requestedBranchId != null && !requestedBranchId.equals(userBranchId)) {
                throw new RuntimeException("Bạn không có quyền truy cập chi nhánh này");
            }

            return userBranchId;
        }

        throw new RuntimeException("Bạn không có quyền truy cập chức năng này");
    }

    // Chặn thao tác ghi/sửa dữ liệu trái phép giữa các chi nhánh.
    // Nếu nhân viên chi nhánh A cố tình thao tác trên tài nguyên chi nhánh B sẽ bị chặn đứng tại đây.

    public void assertSameBranch(Long targetBranchId) {
        User user = currentUserService.getCurrentUser();

        if (user.getUserRole() == UserRole.ADMIN) {
            return;
        }

        if (user.getUserRole() == UserRole.MANAGER || user.getUserRole() == UserRole.STAFF) {
            Long userBranchId = requireUserBranchId(user);
            if (!userBranchId.equals(targetBranchId)) {
                throw new RuntimeException("Bạn không có quyền thao tác trên chi nhánh này");
            }
            return;
        }

        throw new RuntimeException("Bạn không có quyền thao tác chức năng này");
    }

    private Long requireUserBranchId(User user) {
        if (user.getParkingBranch() == null) {
            throw new RuntimeException("Tài khoản chưa được gán chi nhánh");
        }
        return user.getParkingBranch().getParkingBranchId();
    }
}