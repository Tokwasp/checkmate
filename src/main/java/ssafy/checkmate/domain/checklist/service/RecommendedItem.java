package ssafy.checkmate.domain.checklist.service;

import lombok.Getter;

@Getter
public class RecommendedItem {

    private final String name;
    private final String category;
    private final int quantity;

    public RecommendedItem(String name, String category, int quantity) {
        this.name = name;
        this.category = category;
        this.quantity = quantity;
    }
}
