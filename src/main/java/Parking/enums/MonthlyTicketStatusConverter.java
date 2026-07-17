package Parking.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MonthlyTicketStatusConverter implements AttributeConverter<MonthlyTicketStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(MonthlyTicketStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public MonthlyTicketStatus convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        return MonthlyTicketStatus.fromCode(dbData);
    }
}
