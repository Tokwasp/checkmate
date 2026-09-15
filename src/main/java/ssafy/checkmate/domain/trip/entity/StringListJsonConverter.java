package ssafy.checkmate.domain.trip.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

/**
 * List<String>을 별도 테이블 대신 JSON 배열 문자열 컬럼 하나에 저장한다(docs/travel/docs.md §2-1).
 * 조회/검색 조건으로 쓰이지 않는 값이라 컬럼 안을 SQL로 뒤질 필요가 없다.
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return OBJECT_MAPPER.writeValueAsString(attribute != null ? attribute : List.of());
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        return OBJECT_MAPPER.readValue(dbData, OBJECT_MAPPER.getTypeFactory()
                .constructCollectionType(List.class, String.class));
    }
}
