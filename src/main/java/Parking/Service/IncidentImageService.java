package Parking.Service;

import Parking.Model.IncidentImage;
import Parking.Model.IncidentLog;
import Parking.Model.IncidentReport;
import Parking.Model.User;
import Parking.Repository.IncidentImageRepository;
import Parking.Repository.IncidentReportRepository;
import Parking.dto.response.IncidentImageResponse;
import Parking.enums.IncidentLogAction;
import Parking.enums.IncidentStatus;
import Parking.exception.exceptions.ParkingSessionException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IncidentImageService {
    private final IncidentReportRepository incidentReportRepository;
    private final IncidentImageRepository incidentImageRepository;
    private final CurrentUserService currentUserService;
    private final BranchScopeService branchScopeService;
    private final Cloudinary cloudinary;

    @Transactional
    public List<IncidentImageResponse> uploadImages(Long incidentId, List<MultipartFile> files) {
        validateFiles(files);

        User currentUser = currentUserService.getCurrentUser();
        IncidentReport incident = incidentReportRepository.findById(incidentId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo sự cố"));

        assertCanUpload(incident, currentUser);
        assertIncidentAllowsMoreImages(incident, files.size());

        List<String> uploadedPublicIds = new ArrayList<>();

        try {
            List<IncidentImage> images = new ArrayList<>();

            for (MultipartFile file : files) {
                Map<?, ?> result = cloudinary.uploader().upload(
                        file.getBytes(),
                        ObjectUtils.asMap(
                                "folder", "vinparking/incidents/" + incidentId,
                                "resource_type", "image",
                                "unique_filename", true,
                                "overwrite", false
                        )
                );

                Object secureUrlObj = result.get("secure_url");
                Object publicIdObj = result.get("public_id");

                if (secureUrlObj == null || publicIdObj == null) {
                    throw new IOException("Phản hồi từ Cloudinary bị thiếu dữ liệu");
                }

                String imageUrl = secureUrlObj.toString();
                String publicId = publicIdObj.toString();
                uploadedPublicIds.add(publicId);

                IncidentImage image = new IncidentImage();
                image.setImageUrl(imageUrl);
                image.setPublicId(publicId);
                image.setUploadedAt(LocalDateTime.now());
                image.setUploadedBy(currentUser);
                image.setIncidentReport(incident);
                images.add(image);
            }
            
            // Audit log
            IncidentLog log = new IncidentLog();
            log.setChangedBy(currentUser);
            log.setChangedAt(LocalDateTime.now());
            log.setOldStatus(incident.getStatus());
            log.setNewStatus(incident.getStatus());
            log.setActionType(IncidentLogAction.IMAGE_ADDED);
            log.setDescription("Đã thêm " + files.size() + " ảnh bằng chứng");
            incident.addLog(log);

            return incidentImageRepository.saveAllAndFlush(images)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        } catch (IOException ex) {
            uploadedPublicIds.forEach(this::deleteFromCloudinarySilently);
            throw new ParkingSessionException("Không thể upload ảnh sự cố");
        } catch (RuntimeException ex) {
            uploadedPublicIds.forEach(this::deleteFromCloudinarySilently);
            throw ex;
        }
    }
    
    @Transactional(readOnly = true)
    public List<IncidentImageResponse> getImages(Long incidentId) {
        User currentUser = currentUserService.getCurrentUser();
        IncidentReport incident = incidentReportRepository.findById(incidentId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo sự cố"));

        // Phân quyền giống getReportById
        switch (currentUser.getUserRole()) {
            case USER -> {
                if (!incident.getReporter().getUserId().equals(currentUser.getUserId())) {
                    throw new AccessDeniedException("Bạn không có quyền xem thông tin sự cố của người khác!");
                }
            }
            case STAFF, MANAGER -> {
                branchScopeService.assertSameBranch(incident.getParkingBranch().getParkingBranchId());
            }
            case ADMIN -> {}
        }
        
        return incidentImageRepository.findByIncidentReportIncidentIdOrderByUploadedAtAsc(incidentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
    
    @Transactional
    public void deleteImage(Long incidentId, Long imageId) {
        User currentUser = currentUserService.getCurrentUser();
        IncidentReport incident = incidentReportRepository.findById(incidentId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo sự cố"));

        IncidentImage image = incidentImageRepository.findByIncidentImageIdAndIncidentReportIncidentId(imageId, incidentId)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy ảnh trong sự cố"));

        assertCanDelete(incident, image, currentUser);

        try {
            cloudinary.uploader().destroy(image.getPublicId(), ObjectUtils.emptyMap());
        } catch (IOException e) {
            // Log error
        }

        incidentImageRepository.delete(image);
        
        // Audit log
        IncidentLog log = new IncidentLog();
        log.setChangedBy(currentUser);
        log.setChangedAt(LocalDateTime.now());
        log.setOldStatus(incident.getStatus());
        log.setNewStatus(incident.getStatus());
        log.setActionType(IncidentLogAction.IMAGE_REMOVED);
        log.setDescription("Đã xóa ảnh bằng chứng #" + imageId);
        incident.addLog(log);
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ParkingSessionException("Ảnh sự cố không được để trống");
        }

        if (files.size() > 5) {
            throw new ParkingSessionException("Mỗi lần upload tối đa 5 ảnh");
        }

        long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10 MB
        Set<String> allowed = Set.of("image/jpeg", "image/png", "image/webp");

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new ParkingSessionException("File ảnh không được rỗng");
            }
            if (file.getSize() > MAX_IMAGE_SIZE) {
                throw new ParkingSessionException("Mỗi ảnh sự cố không được vượt quá 10 MB");
            }
            if (!allowed.contains(file.getContentType())) {
                throw new ParkingSessionException("Ảnh sự cố phải là JPG, PNG hoặc WEBP");
            }
        }
    }

    private void assertCanUpload(IncidentReport incident, User user) {
        if (incident.getStatus() == IncidentStatus.CANCELLED) {
            throw new ParkingSessionException("Sự cố đã hủy không cho phép tải lên hình ảnh");
        }
        
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            switch (user.getUserRole()) {
                case STAFF, MANAGER, ADMIN -> {
                    // Cho phép bổ sung
                }
                default -> throw new AccessDeniedException("Sự cố đã được giải quyết, chỉ nhân viên mới được phép bổ sung ảnh hậu kiểm");
            }
        }

        switch (user.getUserRole()) {
            case USER -> {
                if (!incident.getReporter().getUserId().equals(user.getUserId())) {
                    throw new AccessDeniedException("Bạn không có quyền thêm ảnh vào sự cố này");
                }
            }
            case STAFF, MANAGER -> {
                branchScopeService.assertSameBranch(incident.getParkingBranch().getParkingBranchId());
            }
            case ADMIN -> {
                // Cho phép.
            }
        }
    }
    
    private void assertCanDelete(IncidentReport incident, IncidentImage image, User user) {
        switch (user.getUserRole()) {
            case USER -> {
                if (!incident.getReporter().getUserId().equals(user.getUserId())) {
                    throw new AccessDeniedException("Bạn không có quyền xóa ảnh của sự cố này");
                }
                if (!image.getUploadedBy().getUserId().equals(user.getUserId())) {
                    throw new AccessDeniedException("Bạn chỉ được xóa ảnh do chính bạn upload");
                }
                if (incident.getStatus() != IncidentStatus.PENDING) {
                    throw new ParkingSessionException("Không thể xóa ảnh khi sự cố không còn ở trạng thái PENDING");
                }
            }
            case STAFF, MANAGER -> {
                branchScopeService.assertSameBranch(incident.getParkingBranch().getParkingBranchId());
            }
            case ADMIN -> {
                // Cho phép.
            }
        }
    }

    private void assertIncidentAllowsMoreImages(IncidentReport incident, int count) {
        long existingImages = incidentImageRepository.countByIncidentReportIncidentId(incident.getIncidentId());
        if (existingImages + count > 10) {
            throw new ParkingSessionException("Mỗi sự cố được phép đính kèm tối đa 10 ảnh");
        }
    }

    private void deleteFromCloudinarySilently(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception ignored) {
        }
    }

    private IncidentImageResponse toResponse(IncidentImage image) {
        return IncidentImageResponse.builder()
                .incidentImageId(image.getIncidentImageId())
                .incidentId(image.getIncidentReport().getIncidentId())
                .imageUrl(image.getImageUrl())
                .uploadedAt(image.getUploadedAt())
                .uploadedById(image.getUploadedBy() != null ? image.getUploadedBy().getUserId() : null)
                .uploadedByName(image.getUploadedBy() != null ? image.getUploadedBy().getUserFullName() : null)
                .build();
    }
}
