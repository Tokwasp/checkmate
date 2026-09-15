package ssafy.checkmate.domain.weather;

import com.fasterxml.jackson.annotation.JsonValue;

public enum WeatherType {
    SUNNY("맑음"),
    CLOUDY("구름"),
    OVERCAST("흐림"),
    RAINY("비"),
    SNOWY("눈");

    private final String label;

    WeatherType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
