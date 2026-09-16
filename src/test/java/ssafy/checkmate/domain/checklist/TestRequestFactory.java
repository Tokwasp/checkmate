package ssafy.checkmate.domain.checklist;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ssafy.checkmate.domain.checklist.dto.request.BagCreateRequest;
import ssafy.checkmate.domain.checklist.dto.request.BagUpdateRequest;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemCreateRequest;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemUpdateRequest;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemsCreateRequest;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemsMoveRequest;
import tools.jackson.databind.ObjectMapper;

final class TestRequestFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestRequestFactory() {
    }

    static BagCreateRequest bagCreateRequest(String name) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        return MAPPER.convertValue(map, BagCreateRequest.class);
    }

    static BagUpdateRequest bagUpdateRequest(String name, String carriageType) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("carriageType", carriageType);
        return MAPPER.convertValue(map, BagUpdateRequest.class);
    }

    static ChecklistItemCreateRequest checklistItemCreateRequest(String name, String category, Integer quantity, Long bagId) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("category", category);
        map.put("quantity", quantity);
        map.put("bagId", bagId);
        return MAPPER.convertValue(map, ChecklistItemCreateRequest.class);
    }

    static ChecklistItemsCreateRequest checklistItemsCreateRequest(List<ChecklistItemCreateRequest> items) {
        Map<String, Object> map = new HashMap<>();
        map.put("items", items.stream().map(TestRequestFactory::toMap).collect(Collectors.toList()));
        return MAPPER.convertValue(map, ChecklistItemsCreateRequest.class);
    }

    static ChecklistItemUpdateRequest checklistItemUpdateRequest(String name, Integer quantity, String category,
                                                                   Boolean checked, Long bagId) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("quantity", quantity);
        map.put("category", category);
        map.put("checked", checked);
        map.put("bagId", bagId);
        return MAPPER.convertValue(map, ChecklistItemUpdateRequest.class);
    }

    static ChecklistItemsMoveRequest checklistItemsMoveRequest(List<Long> itemIds, Long bagId) {
        Map<String, Object> map = new HashMap<>();
        map.put("itemIds", itemIds);
        map.put("bagId", bagId);
        return MAPPER.convertValue(map, ChecklistItemsMoveRequest.class);
    }

    private static Map<String, Object> toMap(ChecklistItemCreateRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", request.getName());
        map.put("category", request.getCategory());
        map.put("quantity", request.getQuantity());
        map.put("bagId", request.getBagId());
        return map;
    }
}
