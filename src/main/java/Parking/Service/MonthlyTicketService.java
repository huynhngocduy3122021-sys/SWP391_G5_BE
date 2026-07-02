package Parking.Service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.MonthlyTicket;
import Parking.Model.ParkingCard;
import Parking.Model.Vehicle;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.VehicleRepository;
import Parking.dto.request.CreateMonthlyTicketRequest;
import Parking.dto.request.UpdateMonthlyTicketRequest;
import Parking.dto.response.MonthlyTicketResponse;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MonthlyTicketService {

    private final MonthlyTicketRepository monthlyTicketRepository;
    private final VehicleRepository vehicleRepository;
    private final ParkingCardRepository parkingCardRepository;

    @Transactional
    public MonthlyTicketResponse createMonthlyTicket(CreateMonthlyTicketRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy phương tiện"));

        ParkingCard parkingCard = parkingCardRepository.findById(request.getParkingCardId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"));

        MonthlyTicket monthlyTicket = new MonthlyTicket();
        monthlyTicket.setVehicle(vehicle);
        monthlyTicket.setParkingCard(parkingCard);
        monthlyTicket.setGuestName(request.getGuestName());
        monthlyTicket.setGuestPhone(request.getGuestPhone());
        monthlyTicket.setStartDate(request.getStartDate());
        monthlyTicket.setEndDate(request.getEndDate());
        monthlyTicket.setStatus(request.getStatus());

        return convertToResponse(monthlyTicketRepository.save(monthlyTicket));
    }

    @Transactional(readOnly = true)
    public List<MonthlyTicketResponse> getAllMonthlyTickets() {
        return monthlyTicketRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyTicketResponse getMonthlyTicketById(Long id) {
        return convertToResponse(findMonthlyTicket(id));
    }

    @Transactional
    public MonthlyTicketResponse updateMonthlyTicket(Long id, UpdateMonthlyTicketRequest request) {
        MonthlyTicket monthlyTicket = findMonthlyTicket(id);

        if (request.getVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy phương tiện"));
            monthlyTicket.setVehicle(vehicle);
        }

        if (request.getParkingCardId() != null) {
            ParkingCard parkingCard = parkingCardRepository.findById(request.getParkingCardId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"));
            monthlyTicket.setParkingCard(parkingCard);
        }

        if (request.getGuestName() != null) {
            monthlyTicket.setGuestName(request.getGuestName());
        }

        if (request.getGuestPhone() != null) {
            monthlyTicket.setGuestPhone(request.getGuestPhone());
        }

        if (request.getStartDate() != null) {
            monthlyTicket.setStartDate(request.getStartDate());
        }

        if (request.getEndDate() != null) {
            monthlyTicket.setEndDate(request.getEndDate());
        }

        if (request.getStatus() != null) {
            monthlyTicket.setStatus(request.getStatus());
        }

        return convertToResponse(monthlyTicketRepository.save(monthlyTicket));
    }

    @Transactional
    public void deleteMonthlyTicket(Long id) {
        MonthlyTicket monthlyTicket = findMonthlyTicket(id);
        monthlyTicketRepository.delete(monthlyTicket);
    }

    private MonthlyTicket findMonthlyTicket(Long id) {
        return monthlyTicketRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy vé tháng"));
    }

    private MonthlyTicketResponse convertToResponse(MonthlyTicket monthlyTicket) {
        return MonthlyTicketResponse.builder()
                .ticketId(monthlyTicket.getTicketId())
                .vehicleId(monthlyTicket.getVehicle().getVehiclesId())
                .licensePlate(monthlyTicket.getVehicle().getLicensePlate())
                .parkingCardId(monthlyTicket.getParkingCard().getParkingCardId())
                .cardCode(monthlyTicket.getParkingCard().getCardCode())
                .guestName(monthlyTicket.getGuestName())
                .guestPhone(monthlyTicket.getGuestPhone())
                .startDate(monthlyTicket.getStartDate())
                .endDate(monthlyTicket.getEndDate())
                .status(monthlyTicket.getStatus())
                .build();
    }
}
