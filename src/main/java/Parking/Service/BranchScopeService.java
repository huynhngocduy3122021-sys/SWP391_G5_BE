package Parking.Service;

import Parking.Model.User;
import Parking.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BranchScopeService {

    @Autowired
    private CurrentUserService currentUserService;

    public Long resolveReadableBranchId(Long requestedBranchId) {
        User user = currentUserService.getCurrentUser();

        if (user.getUserRole() == UserRole.ADMIN) {
            return requestedBranchId; // null = xem tất cả
        }

        if (user.getUserRole() == UserRole.MANAGER || user.getUserRole() == UserRole.STAFF) {
            Long userBranchId = requireUserBranchId(user);

            if (requestedBranchId != null && !requestedBranchId.equals(userBranchId)) {
                throw new RuntimeException("Bạn không có quyền truy cập chi nhánh này");
            }

            return userBranchId;
        }

        throw new RuntimeException("Bạn không có quyền truy cập chức năng này");
    }

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
