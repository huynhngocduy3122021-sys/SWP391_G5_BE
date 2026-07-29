package Parking.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MonthlyTicketRequestStatusConverter implements AttributeConverter<MonthlyTicketRequestStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(MonthlyTicketRequestStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public MonthlyTicketRequestStatus convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        return MonthlyTicketRequestStatus.fromCode(dbData);
    }
}
