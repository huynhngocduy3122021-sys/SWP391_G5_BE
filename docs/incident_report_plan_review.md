# Review logic cho `incident_report_plan.md`

File được review: `docs/incident_report_plan.md`

Mục tiêu review: kiểm tra logic nghiệp vụ, điểm bất hợp lý và rủi ro trong phương pháp thiết kế chức năng Báo cáo & Xử lý sự cố. File này chỉ review tài liệu, không đề xuất code chi tiết.

## Kết luận tổng quan

`incident_report_plan.md` đã tốt hơn bản thiết kế CRUD ban đầu vì đã bổ sung `IncidentType`, ảnh nhiều file, audit log, phân quyền, workflow riêng cho mất thẻ và endpoint nghiệp vụ. Tuy nhiên, tài liệu hiện tại vẫn còn một số logic chưa chặt, đặc biệt ở case mất thẻ, thanh toán, trạng thái thẻ, quyền truy cập và quan hệ dữ liệu.

Điểm cần chỉnh nhất: báo mất thẻ không chỉ là đổi `ParkingCard` sang `LOST`. Hệ thống còn phải đảm bảo checkout, thanh toán và giải phóng phiên gửi xe không vô tình đưa thẻ mất trở lại `AVAILABLE`.

## 1. Workflow mất thẻ vẫn chưa đủ an toàn

Tài liệu đang đề xuất khi báo mất thẻ thì tự động đổi trạng thái thẻ sang `LOST`. Ý tưởng này đúng, nhưng chưa xử lý hết vòng đời của thẻ trong hệ thống.

Vấn đề:

- Luồng thanh toán/check-out hiện tại có xu hướng đưa thẻ về `AVAILABLE` sau khi phiên gửi xe hoàn tất.
- Nếu thẻ đã bị báo mất, sau khi khách thanh toán xong không được trả thẻ đó về `AVAILABLE`.
- Nếu không có quy định rõ, trạng thái `LOST` có thể bị ghi đè sau checkout.

Nên chỉnh logic trong tài liệu:

- Khi incident loại `LOST_CARD` được tạo, thẻ chuyển sang `LOST`.
- Khi checkout phiên có thẻ bị mất, hệ thống chỉ hoàn tất `ParkingSession`, nhưng vẫn giữ `ParkingCard.status = LOST`.
- Thẻ chỉ được chuyển từ `LOST` về `AVAILABLE` khi quản lý xác nhận đã thu hồi được thẻ vật lý.
- Nếu cần dùng thẻ khác để thay thế, phải cấp thẻ mới/thẻ khác, không tái mở thẻ đã mất tự động.

## 2. Thiếu trạng thái riêng cho incident đang chờ thanh toán/xác minh

Hiện workflow chỉ có:

- `PENDING`
- `IN_PROGRESS`
- `RESOLVED`
- `CANCELLED`

Với mất thẻ, xe hư hại hoặc tranh chấp thanh toán, `IN_PROGRESS` hơi rộng. Có những tình huống nhân viên đã xác minh xong nhưng còn chờ khách thanh toán, chờ quản lý duyệt, hoặc chờ đối soát.

Nên cân nhắc bổ sung trạng thái nghiệp vụ hoặc quy ước rõ:

- `PENDING`: mới tạo, chưa ai nhận.
- `IN_PROGRESS`: đang xử lý.
- `WAITING_PAYMENT`: đã xác minh, chờ thanh toán/phí đền bù.
- `WAITING_MANAGER_APPROVAL`: cần quản lý duyệt.
- `RESOLVED`: đã xử lý xong hoàn toàn.
- `CANCELLED`: hủy do sai/trùng/spam.

Nếu không muốn thêm enum, tài liệu phải mô tả rõ `IN_PROGRESS` bao gồm cả các trạng thái chờ này.

## 3. Quan hệ ERD với `ParkingSession`, `ParkingCard`, `Payment` chưa chuẩn

Trong ERD, tài liệu đang mô tả các quan hệ optional nhưng cardinality chưa thật hợp lý.

Vấn đề:

- Một `ParkingSession` có thể phát sinh nhiều incident: mất thẻ, sai phí, va quẹt, tranh chấp.
- Một `ParkingCard` cũng có thể có nhiều incident theo thời gian.
- Một `Payment` có thể liên quan tới một hoặc nhiều incident lỗi thanh toán/đối soát tùy cách thiết kế.

Nên chỉnh trong tài liệu:

- `ParkingSession` 1-n `IncidentReport`.
- `ParkingCard` 1-n `IncidentReport`.
- `Payment` 1-n hoặc 1-1 `IncidentReport` tùy giới hạn nghiệp vụ, nhưng cần ghi rõ.

Không nên mô tả như thể mỗi session/card chỉ gắn tối đa một incident nếu hệ thống cần lưu lịch sử lâu dài.

## 4. `LostCardIncidentRequest` bắt buộc `cardCode` là chưa thực tế

Tài liệu đang yêu cầu người báo mất thẻ cung cấp `cardCode`. Nhưng trong thực tế, khách làm mất thẻ thường không nhớ hoặc không biết mã thẻ.

Vấn đề:

- Người mất thẻ có thể chỉ biết biển số xe, số điện thoại hoặc thông tin xe.
- Nếu bắt buộc `cardCode`, chức năng báo mất thẻ sẽ khó dùng.

Nên chỉnh logic:

- Cho phép tìm phiên gửi xe bằng `parkingSessionId`, biển số xe, số điện thoại, hoặc thông tin xe.
- Nếu có `parkingSessionId`, hệ thống tự suy ra `ParkingCard`.
- `cardCode` chỉ nên là optional, dùng khi nhân viên biết mã thẻ.
- Nếu không xác định được session, tạo incident ở trạng thái `PENDING` và yêu cầu nhân viên xác minh thủ công.

## 5. Chưa kiểm soát case báo mất thẻ trùng lặp

Tài liệu chưa nêu cách xử lý khi cùng một thẻ hoặc cùng một session đã có incident `LOST_CARD` đang mở.

Rủi ro:

- Một session có thể bị tạo nhiều ticket mất thẻ.
- Nhiều nhân viên xử lý trùng một sự cố.
- Log và thanh toán bị nhiễu.

Nên bổ sung rule:

- Không cho tạo incident `LOST_CARD` mới nếu cùng `ParkingSession` hoặc `ParkingCard` đã có incident `LOST_CARD` ở trạng thái chưa đóng.
- Nếu cần, trả về incident đang tồn tại để tiếp tục xử lý.

## 6. `cancelIncident` cần xử lý rollback nghiệp vụ

Tài liệu có endpoint hủy incident, nhưng chưa nói rõ nếu hủy một incident mất thẻ thì trạng thái thẻ xử lý thế nào.

Vấn đề:

- Nếu incident `LOST_CARD` bị hủy do báo nhầm, thẻ có nên chuyển lại `IN_USE` không?
- Nếu thẻ đã được tìm thấy, có nên chuyển lại `AVAILABLE` hay `IN_USE`?
- Nếu phiên gửi xe vẫn active, trạng thái đúng của thẻ thường là `IN_USE`, không phải `AVAILABLE`.

Nên chỉnh logic:

- Hủy incident mất thẻ phải có lý do bắt buộc.
- Nếu báo nhầm và session vẫn `ACTIVE`, thẻ nên quay về `IN_USE`.
- Nếu báo nhầm nhưng session đã `COMPLETED` và thẻ vật lý đã thu hồi, thẻ có thể về `AVAILABLE`.
- Nếu chưa thu hồi thẻ, vẫn giữ `LOST` dù incident bị hủy hoặc đóng theo hướng khác.

## 7. `RESOLVED` cho mất thẻ cần điều kiện đóng rõ ràng

Tài liệu ghi sau khi khách thanh toán và nhận xe thì incident chuyển `RESOLVED`. Logic này hợp lý nhưng cần chặt hơn.

Nên quy định `LOST_CARD` chỉ được `RESOLVED` khi:

- Đã xác minh đúng xe/chủ xe hoặc có xác nhận của staff/manager.
- Phiên gửi xe đã hoàn tất hoặc có biên bản xử lý ngoại lệ.
- Phí gửi xe đã thanh toán.
- Phí mất thẻ đã thanh toán hoặc được manager miễn/ghi chú.
- Trạng thái cuối của thẻ đã được quyết định: giữ `LOST`, chuyển `DISABLED`, hoặc chuyển lại `AVAILABLE` nếu đã thu hồi.

Nếu thiếu các điều kiện này, staff có thể đóng incident quá sớm.

## 8. Thiếu xử lý phí mất thẻ trong mô hình thanh toán

Tài liệu có nhắc "phí gửi xe + phí đền bù mất thẻ", nhưng chưa nói rõ phí này nằm ở đâu.

Vấn đề:

- Entity `Payment` hiện gắn 1-1 với `ParkingSession`.
- Nếu cộng phí mất thẻ vào cùng `Payment.amount`, cần ghi rõ cách phân tách để báo cáo doanh thu.
- Nếu tạo payment riêng cho phí mất thẻ, mô hình hiện tại có thể không hỗ trợ vì `Payment` đang gắn với `ParkingSession`.

Nên bổ sung lựa chọn thiết kế:

- Cách đơn giản: cộng phí mất thẻ vào tổng tiền checkout và lưu ghi chú trong incident.
- Cách rõ ràng hơn: thêm loại khoản thu/phụ phí để tách `parking_fee` và `lost_card_fee`.
- Nếu làm báo cáo tài chính, nên có bảng payment item/payment detail thay vì chỉ một `amount`.

## 9. Phân quyền trong tài liệu còn hơi rộng

Tài liệu cho `USER`, `STAFF`, `MANAGER`, `ADMIN` đều tạo được incident. Điều này hợp lý, nhưng cần giới hạn dữ liệu họ được gắn vào incident.

Rủi ro:

- User có thể tạo incident cho branch/session không thuộc họ.
- Staff có thể tạo sự cố ở chi nhánh không phụ trách.
- Staff có thể xem incident không liên quan nếu chỉ kiểm tra role chung.

Nên chỉnh logic phân quyền:

- User chỉ được tạo incident cho session/xe/tài khoản của họ, hoặc tạo report chung nhưng cần staff xác minh.
- Staff chỉ được tạo/xem incident thuộc chi nhánh họ phụ trách.
- Manager chỉ được quản lý incident trong phạm vi chi nhánh/quyền quản lý.
- Admin mới có quyền toàn hệ thống.

Nếu hệ thống hiện chưa có bảng phân công staff theo branch, tài liệu nên ghi đây là phần cần bổ sung hoặc tạm thời giới hạn bằng role.

## 10. `getMyIncidents` cho Manager/Admin trả về toàn bộ có thể quá rộng

Tài liệu đề xuất Manager/Admin gọi `getMyIncidents` thì trả về toàn bộ incident.

Vấn đề:

- Tên endpoint `my-incidents` nhưng lại trả về toàn bộ cho Manager/Admin, gây khó hiểu.
- Với dữ liệu lớn, trả toàn bộ không phân trang/lọc sẽ chậm.
- Manager thường chỉ nên xem theo chi nhánh phụ trách, không nhất thiết toàn hệ thống.

Nên chỉnh:

- `GET /api/incidents/my` chỉ trả incident liên quan tới người đăng nhập.
- `GET /api/incidents` dành cho Manager/Admin, có filter theo branch/status/type/priority/date.
- Bắt buộc có phân trang cho danh sách incident.

## 11. Thiếu phân trang, lọc và sắp xếp

Incident là dữ liệu vận hành, số lượng sẽ tăng liên tục.

Tài liệu nên bổ sung filter:

- Theo chi nhánh.
- Theo trạng thái.
- Theo loại sự cố.
- Theo độ ưu tiên.
- Theo nhân viên được giao.
- Theo khoảng ngày tạo.

Và nên có sort mặc định:

- `CRITICAL` trước.
- `PENDING` trước.
- Mới nhất trước.

Nếu không có phân trang/lọc, màn hình quản lý sẽ khó dùng khi dữ liệu nhiều.

## 12. Upload ảnh đang phụ thuộc FE upload trước

Tài liệu mô tả request nhận danh sách `imageUrl` và `publicId` đã upload lên Cloudinary trước đó.

Vấn đề:

- Nếu FE upload ảnh thành công nhưng tạo incident thất bại, Cloudinary có ảnh rác.
- Nếu client tự gửi `imageUrl`, có thể gửi URL không thuộc Cloudinary của hệ thống.
- Backend khó kiểm soát file type, size và ownership.

Nên chỉnh:

- Ưu tiên backend nhận multipart upload cho incident hoặc có API upload ảnh sự cố riêng.
- Backend validate MIME type, size, số lượng ảnh.
- Backend lưu `publicId` và rollback ảnh nếu tạo incident thất bại.
- Nếu vẫn cho FE upload trước, phải có cơ chế xác minh `publicId` thuộc folder hợp lệ.

## 13. `IncidentLog` nên ghi cả loại hành động, không chỉ mô tả text

Tài liệu hiện log có old/new status và description. Đây là tốt, nhưng description dạng text khó thống kê.

Nên bổ sung tư duy:

- Log nên có `actionType`: tạo mới, phân công, đổi trạng thái, thêm ảnh, đổi priority, hủy, xác minh, thanh toán.
- `description` chỉ là phần diễn giải thêm.

Lợi ích:

- Dễ lọc lịch sử.
- Dễ audit.
- Dễ thống kê thời gian xử lý theo từng bước.

## 14. Thiếu SLA và escalation cho sự cố nghiêm trọng

Tài liệu có `IncidentPriority` nhưng chưa nói priority dùng để làm gì.

Nên bổ sung:

- `CRITICAL`: cần xử lý ngay, thông báo manager/admin.
- `HIGH`: xử lý trong thời gian ngắn.
- `MEDIUM`: xử lý theo ca.
- `LOW`: xử lý khi có thời gian.

Nếu không có SLA hoặc rule nhắc việc, `priority` chỉ là nhãn hiển thị.

## 15. Sự cố kỹ thuật cần gắn với tài sản/khu vực cụ thể hơn

Tài liệu có `TECHNICAL_ERROR`, `BARRIER_ERROR`, `POWER_OUTAGE`, nhưng entity chỉ gắn với `ParkingBranch`.

Vấn đề:

- Hỏng barrier ở cổng nào?
- Lỗi camera nào?
- Mất điện tầng/khu nào?
- Slot/khu vực nào bị ảnh hưởng?

Nên chỉnh:

- Nếu chưa có bảng thiết bị, tối thiểu thêm mô tả vị trí bắt buộc cho sự cố kỹ thuật.
- Nếu có mở rộng, incident nên optional link tới floor/zone/slot/device.
- Với sự cố ảnh hưởng vận hành, có thể tự động chuyển slot/zone sang `MAINTENANCE` nếu phù hợp.

## 16. Thiếu xử lý bảo mật dữ liệu cá nhân

Incident response hiện dự kiến trả reporter phone, logs, images.

Rủi ro:

- User thường có thể xem số điện thoại hoặc log nội bộ nếu phân quyền không chặt.
- Ảnh hiện trường có thể chứa biển số, mặt người, tài sản cá nhân.

Nên chỉnh:

- Response cho User nên hạn chế thông tin nội bộ.
- Response cho Staff/Manager/Admin có thể đầy đủ hơn.
- Ảnh incident nên dùng URL bảo mật hoặc kiểm soát truy cập nếu cần.

## 17. Tài liệu đang trộn "methodology" với "code mẫu" quá nhiều

Người đọc muốn hiểu phương pháp sẽ bị chìm trong code mẫu dài.

Nên tách:

- File phương pháp: workflow, data model ở mức nghiệp vụ, rule phân quyền, rule trạng thái.
- File triển khai API/code: DTO, entity, service, controller.
- File FE guide: endpoint, request/response, UI behavior.

Cách tách này giúp tránh việc tài liệu nghiệp vụ bị nhầm là code cuối cùng.

## Đề xuất chỉnh `incident_report_plan.md`

Nên cập nhật tài liệu theo thứ tự ưu tiên:

1. Làm rõ workflow mất thẻ: khóa thẻ, checkout, thanh toán, trạng thái cuối của thẻ.
2. Bổ sung rule không tạo trùng incident mất thẻ đang mở.
3. Chỉnh `cardCode` trong báo mất thẻ thành optional, ưu tiên suy ra từ session/biển số.
4. Bổ sung điều kiện đóng `RESOLVED` cho từng loại incident quan trọng.
5. Làm rõ cách tính và lưu phí mất thẻ.
6. Chỉnh endpoint danh sách: tách `my incidents` và danh sách quản lý, thêm phân trang/filter.
7. Làm rõ phân quyền theo ownership và chi nhánh, không chỉ theo role.
8. Bổ sung rollback hoặc xác minh ảnh Cloudinary.
9. Bổ sung action type cho incident log.
10. Tách phần phương pháp và phần code mẫu nếu tài liệu tiếp tục dài.

## Kết luận cuối

`incident_report_plan.md` hiện đã đi đúng hướng, nhưng vẫn cần chỉnh logic quanh mất thẻ và vận hành thực tế. Nếu triển khai theo tài liệu hiện tại mà không chỉnh, rủi ro lớn nhất là thẻ đã báo mất bị đưa lại về `AVAILABLE`, incident bị tạo trùng, user/staff truy cập quá phạm vi, và phí mất thẻ không được ghi nhận rõ ràng trong thanh toán.
