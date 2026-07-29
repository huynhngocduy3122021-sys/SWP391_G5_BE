# Hướng dẫn tách luồng VNPay: checkout và mua gói vé tháng

## 1. Mục tiêu

Hệ thống có hai loại giao dịch VNPay độc lập:

| Loại giao dịch | Giá trị `paymentType` | Trang sau khi VNPay trả về |
|---|---|---|
| Thanh toán phí gửi xe tại cổng ra | `PARKING_SESSION` | `/staff/exit` |
| Thanh toán gói/vé tháng | `MONTHLY_TICKET` | `/payment-result` hoặc `/user-dashboard` |

Backend phải là nơi xác minh chữ ký, số tiền và phân loại giao dịch. Frontend không dùng `localStorage` làm nguồn xác định chính vì dữ liệu có thể bị xóa, còn sót từ giao dịch cũ hoặc không tồn tại khi người dùng đổi tab/thiết bị.

## 2. Luồng đề xuất

Áp dụng cùng một Return URL cho cả hai loại giao dịch:

```text
Frontend tạo thanh toán
        |
        v
Backend tạo Payment và URL VNPay
        |
        v
VNPay thanh toán
        |
        v
/api/payments/vnpay-return
        |
        +-- ParkingSession       -> /staff/exit?paymentType=PARKING_SESSION&...
        |
        +-- MonthlyTicketRequest -> /payment-result?paymentType=MONTHLY_TICKET&...
```

IPN `/api/payments/vnpay-ipn` vẫn được giữ để xác nhận server-to-server. Return và IPN phải xử lý lặp an toàn khi một giao dịch đã được xác nhận trước đó.

## 3. Chỉnh sửa backend

### 3.1. `VnPayService.java`

Trong `createPaymentUrl()`, không tạo Return URL trực tiếp về frontend cho vé tháng. Thay toàn bộ nhánh hiện tại bằng Return URL backend dùng chung:

```java
params.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
```

Xóa đoạn dạng sau:

```java
if (payment.getMonthlyTicketRequest() != null) {
    String returnUrl = vnPayConfig.getFrontendUrl();
    // ...
    returnUrl += "/payment-result?paymentType=MONTHLY_TICKET&requestId="
            + payment.getMonthlyTicketRequest().getId();
    params.put("vnp_ReturnUrl", returnUrl);
} else {
    params.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
}
```

Lý do: nếu VNPay trả thẳng về frontend, `handleVnPayCallback()` không được gọi và frontend không thể tự xác minh chữ ký một cách an toàn.

### 3.2. `PaymentService.java`

Trong `handleVnPayCallback()`, xác định loại thanh toán ngay sau khi lấy `Payment` bằng `vnp_TxnRef`:

```java
ParkingSession parkingSession = payment.getParkingSession();
MonthlyTicketRequest monthlyRequest = payment.getMonthlyTicketRequest();

String paymentType;
if (parkingSession != null) {
    paymentType = "PARKING_SESSION";
} else if (monthlyRequest != null) {
    paymentType = "MONTHLY_TICKET";
} else {
    throw new ParkingSessionException("Giao dịch không gắn với đối tượng thanh toán");
}
```

Mọi `VnpayReturnResponse` được trả ra sau khi đã tìm thấy `Payment` phải chứa đủ thông tin phân loại. Ví dụ với giao dịch thành công:

```java
VnpayReturnResponse.VnpayReturnResponseBuilder responseBuilder =
        VnpayReturnResponse.builder()
                .validSignature(true)
                .success(isSuccess)
                .transactionRef(txnRef)
                .vnpTransactionNo(vnpTxnNo)
                .responseCode(responseCode)
                .paymentType(paymentType);

if (monthlyRequest != null) {
    responseBuilder.requestId(monthlyRequest.getId());

    if (monthlyRequest.getVehicle() != null) {
        responseBuilder.vehicleId(monthlyRequest.getVehicle().getVehicleId());
        responseBuilder.licensePlate(monthlyRequest.getVehicle().getLicensePlate());
    }

    if (monthlyRequest.getPricePolicy() != null) {
        responseBuilder.policyId(monthlyRequest.getPricePolicy().getPricePolicyId());
        responseBuilder.policyName(monthlyRequest.getPricePolicy().getPolicyName());
    }
}

return responseBuilder
        .message(isSuccess ? "Thanh toán thành công" : "Thanh toán thất bại")
        .build();
```

Tên getter ID của `Vehicle` và `PricePolicy` cần đối chiếu với model thực tế trước khi sao chép đoạn trên.

Đặc biệt, nhánh giao dịch đã có trạng thái `PAID` cũng phải trả `paymentType` và `requestId`; nếu thiếu, lần Return đến sau IPN vẫn bị điều hướng sai:

```java
if (payment.getPaymentStatus() == PaymentStatus.PAID) {
    return buildVnPayReturnResponse(payment, true,
            "Thanh toán đã được xác nhận thành công trước đó");
}
```

Nên tách một hàm riêng như `buildVnPayReturnResponse(...)` để tránh bỏ sót trường ở các nhánh thành công, thất bại và đã xử lý.

### 3.3. `PaymentRedirectUrlBuilder.java`

Không chuyển tiếp nguyên bộ tham số VNPay về frontend. Chỉ truyền những trường frontend thực sự cần và encode toàn bộ giá trị:

```java
public URI buildRedirectUri(VnpayReturnResponse response) {
    String targetPath = "PARKING_SESSION".equals(response.getPaymentType())
            ? "/staff/exit"
            : "/payment-result";

    String query = "success=" + response.isSuccess()
            + "&paymentType=" + encode(response.getPaymentType())
            + "&transactionRef=" + encode(response.getTransactionRef())
            + "&responseCode=" + encode(response.getResponseCode())
            + "&message=" + encode(response.getMessage());

    if (response.getRequestId() != null) {
        query += "&requestId=" + response.getRequestId();
    }

    String baseUrl = frontendUrl.endsWith("/")
            ? frontendUrl.substring(0, frontendUrl.length() - 1)
            : frontendUrl;

    return URI.create(baseUrl + targetPath + "?" + query);
}

private String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
}
```

Sau khi bỏ đối số `rawParams`, sửa controller:

```java
URI redirectUri = paymentRedirectUrlBuilder.buildRedirectUri(response);
```

Nếu muốn giữ chữ ký hàm hiện tại để giảm phạm vi chỉnh sửa, có thể vẫn nhận `rawParams` nhưng không chuyển tiếp chúng về frontend.

### 3.4. Xử lý callback lỗi

Khi chữ ký sai hoặc TMN code sai, backend chưa tìm được loại giao dịch đáng tin cậy. Nên điều hướng về `/payment-result` với `success=false`, không dựa vào `paymentType` do query gửi lên vì query chưa được xác thực.

Không hiển thị trực tiếp thông báo lấy từ query nếu chưa escape ở frontend.

## 4. Chỉnh sửa frontend

Các đường dẫn dưới đây dựa trên cấu trúc frontend được mô tả. Nếu frontend nằm ở repository khác, áp dụng thay đổi tại repository đó.

### 4.1. `src/components/staff/GateOutPanel.jsx`

Không cần dùng `localStorage` để phân loại giao dịch nữa. Khi API checkout trả `paymentUrl`, chỉ mở hoặc chuyển hướng đến URL đó:

```jsx
if (result.paymentMethod === "VNPAY" && result.paymentUrl) {
  window.location.assign(result.paymentUrl);
  return;
}
```

Xóa các thao tác:

```jsx
localStorage.setItem("paymentType", "CHECKOUT");
localStorage.removeItem("paymentType");
```

Nếu vẫn muốn giữ cờ này như phương án hỗ trợ giao diện, dùng key cụ thể như `pendingVnPayCheckout` và tuyệt đối không dùng nó để xác nhận giao dịch đã thanh toán.

Khi `/staff/exit` được tải lại, đọc kết quả backend đã xác minh:

```jsx
const [searchParams, setSearchParams] = useSearchParams();

useEffect(() => {
  const paymentType = searchParams.get("paymentType");
  if (paymentType !== "PARKING_SESSION") return;

  const success = searchParams.get("success") === "true";
  const message = searchParams.get("message") ||
    (success ? "Thanh toán thành công" : "Thanh toán thất bại");

  if (success) {
    // Hiển thị thông báo và tải lại danh sách/phiên đang xử lý.
  } else {
    // Hiển thị lỗi và cho phép nhân viên thử thanh toán lại.
  }

  setSearchParams({}, { replace: true });
}, [searchParams, setSearchParams]);
```

Việc xóa query bằng `replace` ngăn thông báo xuất hiện lại khi refresh trang.

### 4.2. `src/pages/PaymentResultPage.jsx`

Trang này chỉ xử lý thanh toán vé tháng. Bỏ logic đọc `localStorage` và tự chuyển `/staff/exit`:

```jsx
useEffect(() => {
  const paymentType = searchParams.get("paymentType");

  if (paymentType !== "MONTHLY_TICKET") {
    // Hiển thị kết quả không hợp lệ hoặc chuyển về dashboard phù hợp.
    return;
  }

  const success = searchParams.get("success") === "true";
  const requestId = searchParams.get("requestId");
  const transactionRef = searchParams.get("transactionRef");
  const message = searchParams.get("message");

  setResult({ success, requestId, transactionRef, message });
}, [searchParams]);
```

Không dùng riêng `vnp_ResponseCode === "00"` ở frontend để xác nhận thành công. Tham số `success` phải do backend tạo sau khi đã:

- xác minh `vnp_SecureHash`;
- kiểm tra `vnp_TmnCode`;
- đối chiếu `vnp_Amount` với `Payment.amount`;
- cập nhật `PaymentStatus` và đối tượng nghiệp vụ liên quan.

### 4.3. Router frontend

Đảm bảo hai route đều tồn tại và không bị route bảo vệ làm mất query:

```jsx
<Route path="/staff/exit" element={<GateOutPanel />} />
<Route path="/payment-result" element={<PaymentResultPage />} />
```

`/staff/exit` yêu cầu tài khoản nhân viên. Nếu hệ thống đăng nhập hết hạn trong lúc thanh toán, trang đăng nhập phải lưu cả pathname và query để quay lại đúng kết quả sau khi đăng nhập.

## 5. Phân biệt dữ liệu hai loại giao dịch

Không phân biệt bằng chuỗi `TXN_` hay `TXN_MT_` nếu đã có quan hệ trong database. Prefix chỉ hữu ích cho việc đọc log.

| Trường | Checkout | Vé tháng |
|---|---|---|
| `Payment.parkingSession` | Có | Không |
| `Payment.monthlyTicketRequest` | Không | Có |
| Thành công | Session `COMPLETED`, trả thẻ nếu không mất | Request `PENDING_APPROVAL` |
| Trang kết quả | `/staff/exit` | `/payment-result` |

Nên đảm bảo mỗi `Payment` chỉ gắn đúng một trong hai quan hệ. Có thể kiểm tra ở service và thêm constraint database nếu thiết kế database cho phép.

## 6. Các trường hợp kiểm thử bắt buộc

### Checkout

1. Thanh toán thành công: về `/staff/exit`, session thành `COMPLETED`, payment thành `PAID`.
2. Người dùng hủy thanh toán: về `/staff/exit`, payment thành `FAILED` hoặc trạng thái phù hợp với thiết kế.
3. Sai số tiền: backend trả thất bại, không hoàn tất session.
4. IPN đến trước Return: Return vẫn trả `PARKING_SESSION` và điều hướng đúng.
5. Return đến trước IPN: IPN xử lý lặp an toàn.
6. Thẻ bị mất: thanh toán xong không chuyển thẻ `LOST` thành `AVAILABLE`.

### Vé tháng

1. Thanh toán thành công: về `/payment-result`, payment `PAID`, request `PENDING_APPROVAL`.
2. Thanh toán thất bại/hủy: request không chuyển sang `PENDING_APPROVAL`.
3. Sai chữ ký hoặc số tiền: không cập nhật thành công.
4. IPN đến trước Return: trang kết quả vẫn nhận `MONTHLY_TICKET` và `requestId`.
5. Refresh trang kết quả: không tạo giao dịch mới và không xử lý nghiệp vụ lần hai.

## 7. Kết quả mong đợi

- Checkout luôn trở lại `/staff/exit`.
- Mua gói luôn trở lại `/payment-result`.
- Hai luồng được phân biệt bằng bản ghi `Payment` ở backend.
- Frontend chỉ hiển thị kết quả backend đã xác minh.
- Không còn phụ thuộc vào `localStorage` để quyết định loại giao dịch hoặc trạng thái thanh toán.
