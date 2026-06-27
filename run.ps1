# PowerShell Script để thiết lập biến môi trường và chạy ứng dụng Spring Boot
# Chạy script bằng cách mở PowerShell tại thư mục dự án và gõ: .\run.ps1

# Cấu hình Cloudinary (Lấy từ thông tin bạn cung cấp)
$env:CLOUDINARY_CLOUD_NAME="du2om5fxt"        
$env:CLOUDINARY_API_KEY="537718225398428" 
$env:CLOUDINARY_API_SECRET="OrTOmLWLm88RSpT4_JnJIDGgGXk"

# Cấu hình VNPay Sandbox
$env:VNPAY_ENABLED="true"
$env:VNPAY_TMN_CODE="K086N71M"        # Thay bằng TMN_CODE thực tế của bạn
$env:VNPAY_HASH_SECRET="CIHEVP5OH4WMTSDZYHF0QBGA1Y1C1OBU"  # Thay bằng HASH_SECRET thực tế của bạn

# URL Ngrok (Thay thế bằng URL ngrok mới nếu bạn không dùng static domain)
$env:VNPAY_RETURN_URL="https://bullpen-viewer-overfill.ngrok-free.dev/api/payments/vnpay-return"
$env:VNPAY_IPN_URL="https://skimmed-reseal-oxidize.ngrok-free.dev/api/payments/vnpay-ipn"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Đã thiết lập các biến môi trường thành công! " -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Đang khởi chạy dự án Spring Boot (port 8081)..." -ForegroundColor Yellow

# Chạy ứng dụng Spring Boot qua Maven
mvn spring-boot:run
