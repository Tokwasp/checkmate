package ssafy.checkmate.domain.checklist.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * 언어 코드 → 검색 별칭을 별도 테이블 대신 JSON 객체 문자열 컬럼 하나에 저장한다.
 * 조회 조건으로 쓰이지 않고 물품 하나를 읽을 때 통째로 같이 읽는 값이라 컬럼 안을
 * SQL로 뒤질 일이 없다({@code StringListJsonConverter}와 같은 이유).
 */
@Converter
public class SearchAliasJsonConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        return OBJECT_MAPPER.writeValueAsString(attribute != null ? attribute : Map.of());
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.readValue(dbData, OBJECT_MAPPER.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, String.class));
    }
}
