package Parking.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import Parking.Model.ParkingSession;
import Parking.Model.VehicleImage;
import Parking.Model.VehicleImageType;
import Parking.Repository.ParkingSessionRepository;
import Parking.Repository.VehicleImageRepository;
import Parking.dto.response.VehicleImageResponse;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VehicleImageService {

    private static final long MAX_FILE_SIZE =
        10L * 1024L * 1024L;

    private final VehicleImageRepository vehicleImageRepository;

    private final ParkingSessionRepository parkingSessionRepository;

    private final Cloudinary cloudinary;

    @Transactional
public List<VehicleImageResponse> uploadVehicleImages(
        Long parkingSessionId,
        VehicleImageType imageType,
        List<MultipartFile> files
) {
    if (files == null || files.isEmpty()) {
        throw new ParkingSessionException(
                "Image files cannot be empty"
        );
    }

    if (files.size() > 5) {
        throw new ParkingSessionException(
                "Cannot upload more than 5 images at once"
        );
    }

    if (imageType == null) {
        throw new ParkingSessionException(
                "Image type is required"
        );
    }

    ParkingSession parkingSession =
            parkingSessionRepository
                    .findById(parkingSessionId)
                    .orElseThrow(() ->
                            new ParkingSessionException(
                                    "Parking session not found"
                            )
                    );

    List<String> uploadedPublicIds = new ArrayList<>();
    List<VehicleImage> vehicleImages = new ArrayList<>();

    try {
        String folder =
                "vinparking/parking-sessions/"
                + parkingSessionId
                + "/"
                + imageType.name() .toLowerCase(Locale.ROOT);

        for (MultipartFile file : files) {
            validateImage(file);

            Map<?, ?> uploadResult =
                    cloudinary.uploader().upload(
                            file.getBytes(),
                            ObjectUtils.asMap(
                                    "folder", folder,
                                    "resource_type", "image",
                                    "unique_filename", true,
                                    "overwrite", false
                            )
                    );

            Object secureUrlValue = uploadResult.get("secure_url");

            Object publicIdValue = uploadResult.get("public_id");

            if (secureUrlValue == null || publicIdValue == null) {
                throw new ParkingSessionException( "Cloudinary did not return image information");
            }

            String imageUrl =secureUrlValue.toString();

            String publicId =publicIdValue.toString();

            uploadedPublicIds.add(publicId);

            VehicleImage vehicleImage = new VehicleImage();

            vehicleImage.setImageUrl(imageUrl);
            vehicleImage.setPublicId(publicId);
            vehicleImage.setImageType(imageType);
            vehicleImage.setUploadedAt(LocalDateTime.now());
            vehicleImage.setParkingSession(parkingSession);

            vehicleImages.add(vehicleImage);
        }

        /*
         * Lưu toàn bộ ảnh vào database.
         * flush để lỗi database xảy ra ngay trong khối try,
         * từ đó có thể xóa ảnh rác trên Cloudinary.
         */
        List<VehicleImage> savedImages = vehicleImageRepository.saveAllAndFlush(vehicleImages);

        return savedImages.stream()
                .map(this::convertToResponse)
                .toList();

    } catch (IOException exception) {
        uploadedPublicIds.forEach(this::deleteFromCloudinarySilently);

        throw new ParkingSessionException("Cannot upload images to Cloudinary");

    } catch (RuntimeException exception) {
        /*
         * Nếu một ảnh upload hoặc lưu database thất bại,
         * xóa những ảnh đã upload trước đó khỏi Cloudinary.
         */
        uploadedPublicIds.forEach(this::deleteFromCloudinarySilently
        );

        throw exception;
    }
}

    @Transactional(readOnly = true)
    public List<VehicleImageResponse> getImagesBySession( Long parkingSessionId) {
        if (!parkingSessionRepository.existsById(parkingSessionId)) {
            throw new ParkingSessionException("Parking session not found");
        }

        return vehicleImageRepository
            .findByParkingSessionParkingSessionIdOrderByUploadedAtAsc( parkingSessionId)
            .stream()
            .map(this::convertToResponse)
            .toList();
    }

    private VehicleImageResponse convertToResponse(VehicleImage vehicleImage) {
        return VehicleImageResponse.builder()
            .vehicleImageId(vehicleImage.getVehicleImageId())
            .parkingSessionId(vehicleImage.getParkingSession().getParkingSessionId())
            .imageUrl(vehicleImage.getImageUrl())
            .imageType(vehicleImage.getImageType())
            .uploadedAt(vehicleImage.getUploadedAt())
            .build();
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ParkingSessionException( "Image file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ParkingSessionException( "Image size cannot exceed 10 MB");
        }

        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("image/")) {

            throw new ParkingSessionException("Uploaded file must be an image");
        }
    }

    private void deleteFromCloudinarySilently(
            String publicId
    ) {
        try {
            cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap(
                    "resource_type", "image",
                    "invalidate", true
                )
            );
        } catch (IOException ignored) {
            // Giữ lại exception database ban đầu.
        }
    }
}