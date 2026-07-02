package Parking.Controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import Parking.Service.MonthlyTicketService;
import Parking.dto.request.CreateMonthlyTicketRequest;
import Parking.dto.request.UpdateMonthlyTicketRequest;
import Parking.dto.response.MonthlyTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@SecurityRequirement(name = "api_key")
@RequestMapping("/api/monthly-tickets")
@RequiredArgsConstructor
@CrossOrigin("*")
public class MonthlyTicketController {

    private final MonthlyTicketService monthlyTicketService;

    @PostMapping
    @Operation(summary = "Tạo vé tháng")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketResponse> createMonthlyTicket(
            @Valid @RequestBody CreateMonthlyTicketRequest request
    ) {
        return ResponseEntity.ok(monthlyTicketService.createMonthlyTicket(request));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách vé tháng")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketResponse>> getAllMonthlyTickets(
            @RequestParam(required = false) Long branchId
    ) {
        return ResponseEntity.ok(monthlyTicketService.getAllMonthlyTickets(branchId));
    }

    @GetMapping("/my-tickets")
    @Operation(summary = "Lấy danh sách vé tháng của cư dân đang đăng nhập")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketResponse>> getMyMonthlyTickets() {
        return ResponseEntity.ok(monthlyTicketService.getMyMonthlyTickets());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy vé tháng theo ID")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketResponse> getMonthlyTicketById(@PathVariable Long id) {
        return ResponseEntity.ok(monthlyTicketService.getMonthlyTicketById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin vé tháng")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketResponse> updateMonthlyTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMonthlyTicketRequest request
    ) {
        return ResponseEntity.ok(monthlyTicketService.updateMonthlyTicket(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa vé tháng")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteMonthlyTicket(@PathVariable Long id) {
        monthlyTicketService.deleteMonthlyTicket(id);
        return ResponseEntity.noContent().build();
    }
}
