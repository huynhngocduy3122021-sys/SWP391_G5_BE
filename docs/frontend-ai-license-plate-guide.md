# Hướng dẫn Frontend Tích hợp API AI Nhận diện và So khớp Biển số xe

Tài liệu này hướng dẫn team Frontend cách gọi API để upload ảnh chụp biển số xe và gửi kèm biển số người dùng nhập để Backend (sử dụng PlateRecognizer AI) thực hiện so khớp.

## 1. Thông tin API

- **Endpoint:** `/api/license-plate/verify`
- **Method:** `POST`
- **Content-Type:** `multipart/form-data` (Không cần set thủ công trong Header nếu dùng `FormData` của JS).
- **Authentication:** Yêu cầu gửi kèm Token ở Header (`Authorization: Bearer <token>`).

## 2. Các tham số yêu cầu (Payload)

Vì API nhận file nên chúng ta bắt buộc dùng `FormData` thay vì JSON thông thường.

| Tên trường (Key) | Kiểu dữ liệu | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `licensePlate` | `string` | Có | Chuỗi biển số do nhân viên tự gõ hoặc khách hàng đăng ký (ví dụ: `51H-123.45`). |
| `image` | `File` (Blob) | Có | File ảnh chụp từ camera tại bãi xe. Chỉ chấp nhận các định dạng ảnh: `JPG`, `PNG`, `WEBP`. Giới hạn kích thước tối đa 5MB. |

## 3. Cấu trúc Response trả về

Nếu gọi API thành công (HTTP Status 200), Backend sẽ trả về cục JSON sau:

```json
{
  "inputLicensePlate": "51H-123.45",
  "detectedLicensePlate": "51H12345",
  "normalizedInput": "51H12345",
  "normalizedDetected": "51H12345",
  "matched": true,
  "confidence": 0.893,
  "message": "Biển số khớp"
}
```

### Ý nghĩa các trường trả về:
- `inputLicensePlate`: Biển số ban đầu Frontend gửi lên.
- `detectedLicensePlate`: Biển số thô mà AI (PlateRecognizer) đọc được từ ảnh.
- `normalizedInput`: Biển số Frontend gửi lên đã được backend "làm sạch" (viết hoa, xóa dấu cách, dấu gạch ngang...).
- `normalizedDetected`: Biển số AI đọc được đã được "làm sạch".
- `matched` **(Quan trọng)**: `true` nếu AI đọc ra trùng với biển số Frontend nhập và độ tự tin (confidence) của AI >= 75%. `false` nếu không khớp hoặc hình mờ AI không nhận diện được. 
- `confidence`: Độ tin cậy của kết quả AI trả về (Từ 0.0 đến 1.0).
- `message`: Thông báo chi tiết cho nhân viên/user (ví dụ: "Biển số khớp", "Biển số không khớp", "AI đọc biển số chưa đủ tin cậy, vui lòng chụp lại ảnh").

## 4. Code mẫu bằng JavaScript (Fetch API / Axios)

### Dùng Fetch API:

```javascript
async function verifyLicensePlate(imageFile, plateNumber, token) {
  try {
    const formData = new FormData();
    formData.append("licensePlate", plateNumber);
    formData.append("image", imageFile);

    const response = await fetch("http://localhost:8081/api/license-plate/verify", {
      method: "POST",
      headers: {
        // LƯU Ý: Tuyệt đối KHÔNG set Content-Type: multipart/form-data ở đây
        // Trình duyệt sẽ tự động tạo Header này kèm theo chuỗi Boundary thích hợp.
        "Authorization": `Bearer ${token}`
      },
      body: formData
    });

    if (!response.ok) {
      throw new Error("Lỗi khi gọi API hệ thống");
    }

    const data = await response.json();
    
    if (data.matched) {
      alert("Thành công: " + data.message);
      // Thực hiện tiếp luồng mở barie, tạo session...
    } else {
      alert("Thất bại: " + data.message);
      // Yêu cầu nhân viên chụp lại hình hoặc check thủ công
    }

  } catch (error) {
    console.error("Lỗi:", error);
    alert("Không thể kết nối đến server kiểm tra biển số.");
  }
}
```

### Dùng Axios:

```javascript
import axios from 'axios';

async function verifyLicensePlateAxios(imageFile, plateNumber, token) {
  try {
    const formData = new FormData();
    formData.append("licensePlate", plateNumber);
    formData.append("image", imageFile);

    const response = await axios.post(
      'http://localhost:8081/api/license-plate/verify',
      formData,
      {
        headers: {
          'Authorization': `Bearer ${token}`
          // Axios cũng tự nhận diện FormData và set Content-Type tự động
        }
      }
    );

    const data = response.data;
    
    if (data.matched) {
       console.log("Khớp biển số!");
    } else {
       console.warn("Không khớp:", data.message);
    }
  } catch (error) {
    console.error("Lỗi call API:", error.response?.data || error.message);
  }
}
```

## 5. Xử lý các tình huống lỗi có thể xảy ra (Error Handling)

Frontend cần bắt các exception từ Backend trả về (HTTP Status 400 hoặc 500) tương ứng với các tình huống sau:
- Không gửi ảnh (Thiếu trường `image`).
- Không gửi text (Thiếu trường `licensePlate`).
- File ảnh gửi lên không đúng định dạng (Ví dụ: gửi file `.pdf`).
- File ảnh dung lượng lớn hơn quy định. 

Mọi logic "Không khớp" (mờ, sai biển, không nhận ra biển) đều vẫn sẽ trả về `HTTP 200 OK` đi kèm `matched = false` và `message` diễn giải lý do. Do đó, Frontend chỉ cần check field `matched` để rẽ nhánh UI hợp lý.
