package ssafy.checkmate.domain.checklist.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import ssafy.checkmate.domain.checklist.exception.InvalidCarriageTypeException;

public enum CarriageType {
    CABIN("기내"),
    CHECKED("위탁");

    private final String label;

    CarriageType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    public static CarriageType parseUserInput(String value) {
        for (CarriageType type : values()) {
            if (type.label.equals(value)) {
                return type;
            }
        }
        throw new InvalidCarriageTypeException();
    }
}
