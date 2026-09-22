package ssafy.checkmate.domain.checklist.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.response.SecurityCheckItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.SecurityCheckResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.BagItemRiskLevel;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.domain.checklist.entity.RiskCheckStep;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;
import ssafy.checkmate.domain.checklist.exception.NoItemsToCheckException;
import ssafy.checkmate.domain.checklist.exception.SecurityCheckFailedException;
import ssafy.checkmate.domain.checklist.exception.SecurityCheckLimitExceededException;
import ssafy.checkmate.domain.checklist.exception.UnsupportedCountryException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagItemRiskLevelRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.repository.RiskLevelRepository;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.exception.BusinessException;

/**
 * 반입 규정 검사(30번). 여행에서 실제로 가방에 담은 물품을 가방(과 carriageType)별로 묶어
 * 항공 보안 규정 지식베이스에 물어보고, 받은 판정을 물품마다 되돌려 붙인다.
 *
 * 가방별이 아니라 여행 전체를 한 번에 검사한다 — 가방별 필터링은 프론트가 응답 전체를 받아
 * 처리한다. 다만 <b>담긴 물품(departureChecked)만</b> 검사한다 — 안 가져가는 물건까지 판정하면
 * 화면에 없는 금지 물품이 뜨고, 그만큼 규정 질의 비용도 헛되이 나간다.
 *
 * 로그는 EC2에서 흐름을 따라갈 수 있도록 {@code [security-check]} 접두사로 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecurityCheckService {

    /**
     * 계정당 허용하는 누적 검사 횟수. 이 기능은 한 번 호출할 때마다 Bedrock 검색·생성 비용이
     * 실제로 나가므로 상한이 없으면 비용이 무한정 늘어난다.
     */
    private static final int SECURITY_CHECK_LIMIT = 20;

    private final BagItemRepository bagItemRepository;
    private final BagRepository bagRepository;
    private final BagItemRiskLevelRepository bagItemRiskLevelRepository;
    private final RiskLevelRepository riskLevelRepository;
    private final TripRepository tripRepository;
    private final ItemTranslationClient itemTranslationClient;
    private final MemberRepository memberRepository;
    private final AviationRegulationClient aviationRegulationClient;

    /**
     * 규정 조회가 실패해도(500) <b>소모한 호출 횟수는 되돌리지 않는다</b>.
     * 실패하더라도 Bedrock 검색 비용은 이미 나갔고, 롤백하면 같은 요청을 무한히 재시도할 수
     * 있어 상한이 무력해지기 때문이다.
     */
    @Transactional(noRollbackFor = SecurityCheckFailedException.class)
    public SecurityCheckResponse check(Long tripId, Long memberId) {
        Trip trip = findTripOwnedBy(tripId, memberId);
        CountryJurisdiction jurisdiction = resolveJurisdiction(trip);

        List<BagItem> items = findItemsToCheck(tripId);
        Map<Long, Bag> bags = findBagsOf(tripId);
        Map<ItemGroup, List<BagItem>> groups = groupForQuery(items, bags);
        log.info("[security-check] 검사 대상 tripId={} country={} 물품={}개 → 질의 묶음={}건",
                tripId, jurisdiction, items.size(), groups.size());

        consumeQuota(memberId);
        Map<Long, SecurityVerdict> verdicts = askRegulation(jurisdiction, groups);

        List<SecurityCheckItemResponse> responses = applyVerdicts(groups, bags, verdicts);
        return summarize(responses);
    }

    /**
     * 담긴 물품(departureChecked)만 검사한다 — 체크리스트에만 있고 아직 안 담은 물품은 빼고 본다.
     * 실제로 들고 가지 않는 물건의 판정은 사용자에게 의미가 없고, 규정 질의 비용만 늘린다.
     *
     * 체크리스트에 물품이 있어도 담긴 게 하나도 없으면 400이다 — 귀가 전 점검(31번)과 같은 기준이라
     * 두 화면이 같은 물품 집합을 본다.
     */
    private List<BagItem> findItemsToCheck(Long tripId) {
        List<BagItem> items = bagItemRepository.findAllByTripIdAndDepartureCheckedOrderByIdAsc(tripId, true);
        if (items.isEmpty()) {
            log.info("[security-check] 검사할 물품이 없다 — 담긴 물품이 0개다 tripId={}", tripId);
            throw new NoItemsToCheckException();
        }
        return items;
    }

    /** 코퍼스가 다루지 않는 국가는 검사 자체를 시작하지 않는다 — 규정 호출 전에 걸러낸다. */
    private CountryJurisdiction resolveJurisdiction(Trip trip) {
        return CountryJurisdiction.from(trip.getCountry())
                .orElseThrow(() -> {
                    log.info("[security-check] 지식베이스가 다루지 않는 국가 tripId={} country={}",
                            trip.getId(), trip.getCountry());
                    return new UnsupportedCountryException();
                });
    }

    private Map<Long, Bag> findBagsOf(Long tripId) {
        Map<Long, Bag> bags = new HashMap<>();
        bagRepository.findAllByTripId(tripId).forEach(bag -> bags.put(bag.getId(), bag));
        return bags;
    }

    /**
     * 가방·carriageType·이름이 같은 물품을 한 묶음으로 만든다. 판정이 어차피 같으므로 한 번만
     * 묻고 받은 판정 하나를 묶음 전체에 복사한다 — 프롬프트와 응답 길이를 줄이기 위해서다.
     */
    private Map<ItemGroup, List<BagItem>> groupForQuery(List<BagItem> items, Map<Long, Bag> bags) {
        Map<ItemGroup, List<BagItem>> groups = new LinkedHashMap<>();
        items.stream()
                .sorted(Comparator.comparing(BagItem::getId))
                .forEach(item -> groups
                        .computeIfAbsent(ItemGroup.of(item, bags.get(item.getBagId())), key -> new ArrayList<>())
                        .add(item));
        return groups;
    }

    /**
     * 묶음 목록을 한 번에 물어보고, 대표 itemId로 찾을 수 있게 판정을 정리한다.
     * 규정 조회 자체가 실패하면 검사를 500으로 끝낸다 — 일부만 판정된 결과를 성공처럼
     * 돌려주면 화면이 "통과"로 오해하기 때문이다.
     */
    private Map<Long, SecurityVerdict> askRegulation(CountryJurisdiction jurisdiction,
            Map<ItemGroup, List<BagItem>> groups) {
        Map<String, String> localTerms = translateNames(jurisdiction, groups.keySet());
        List<SecurityCheckTarget> targets = groups.entrySet().stream()
                .map(entry -> entry.getKey().toTarget(representativeIdOf(entry.getValue()),
                        localTerms.getOrDefault(entry.getKey().name(), "")))
                .toList();

        List<SecurityVerdict> verdicts = callClient(jurisdiction, targets);
        if (verdicts.size() != targets.size()) {
            log.warn("[security-check] 보낸 묶음 {}건 중 {}건만 판정이 왔다 — 나머지는 통과로 둔다",
                    targets.size(), verdicts.size());
        }

        Map<Long, SecurityVerdict> byItemId = new HashMap<>();
        verdicts.forEach(verdict -> byItemId.put(verdict.getItemId(), verdict));
        warnUnknownItemIds(targets, byItemId);
        return byItemId;
    }

    /** 요청에 없던 itemId 판정은 조용히 버려지므로, 모델이 ID를 잘못 돌려줬는지 알 수 있게 남긴다. */
    private void warnUnknownItemIds(List<SecurityCheckTarget> targets, Map<Long, SecurityVerdict> verdicts) {
        Set<Long> sentIds = targets.stream().map(SecurityCheckTarget::getItemId).collect(Collectors.toSet());
        verdicts.keySet().stream()
                .filter(itemId -> !sentIds.contains(itemId))
                .forEach(itemId -> log.warn("[security-check] 요청에 없던 itemId={} 판정이 왔다 — 무시한다", itemId));
    }

    /**
     * 호출 횟수를 하나 소모한다. 상한을 넘겼으면 429로 거절하고 Bedrock을 부르지 않는다.
     *
     * "확인"과 "증가"를 조건부 UPDATE 한 문장으로 처리한다 — 따로 조회한 뒤 증가시키면
     * 동시 요청 두 개가 같은 횟수를 읽고 둘 다 통과한다.
     *
     * 소모는 <b>본인 여행·지원 국가·물품 존재 검증을 모두 통과한 뒤</b>에 한다. 잘못된 요청
     * (403·404·422·400)으로 횟수를 잃으면 안 되기 때문이다.
     */
    private void consumeQuota(Long memberId) {
        if (memberRepository.consumeSecurityCheckQuota(memberId, SECURITY_CHECK_LIMIT) == 0) {
            log.info("[security-check] 호출 횟수 소진 memberId={} 상한={}", memberId, SECURITY_CHECK_LIMIT);
            throw new SecurityCheckLimitExceededException();
        }
    }

    /**
     * 물품 이름을 그 나라 말 검색어로 옮긴다. 규정 문서가 현지어라 한국어 이름만으로는 임베딩이
     * 매칭하지 못해 규정을 통째로 놓친다 — 질의에 현지어를 함께 실어야 검색이 걸린다.
     *
     * 이름마다 부르지 않고 묶음 이름 전체를 한 번에 넘긴다. 규정 문서가 한국어인 한국행은 옮길
     * 필요가 없어 아예 부르지 않는다.
     *
     * 못 받은 이름은 한국어로만 검색하게 되는데, 그 물품은 규정을 못 찾아 조용히 통과로 판정될
     * 수 있다. 나중에 "왜 안 걸렸나"를 따라갈 수 있도록 경고로 남긴다.
     */
    private Map<String, String> translateNames(CountryJurisdiction jurisdiction, Set<ItemGroup> groups) {
        if (!jurisdiction.needsTranslation()) {
            return Map.of();
        }
        List<String> names = groups.stream().map(ItemGroup::name).distinct().toList();
        Map<String, String> localTerms = itemTranslationClient.translate(names, jurisdiction);

        List<String> missing = names.stream().filter(name -> !localTerms.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            log.warn("[security-check] 현지어 검색어를 못 받은 물품 — 한국어로만 검색한다 country={} 물품={}",
                    jurisdiction, missing);
        }
        return localTerms;
    }

    private List<SecurityVerdict> callClient(CountryJurisdiction jurisdiction, List<SecurityCheckTarget> targets) {
        try {
            return aviationRegulationClient.judge(jurisdiction, targets);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[security-check] 규정 조회 실패 jurisdiction={} 묶음={}건",
                    jurisdiction, targets.size(), e);
            throw new SecurityCheckFailedException();
        }
    }

    /**
     * 묶음마다 받은 판정을 그 묶음의 물품 전부에 적용한다. 판정이 안 온 묶음은 통과로 두고,
     * 요청에 없던 itemId가 섞여 와도 여기서 자연히 무시된다(대표 ID로만 찾기 때문).
     */
    private List<SecurityCheckItemResponse> applyVerdicts(Map<ItemGroup, List<BagItem>> groups,
            Map<Long, Bag> bags, Map<Long, SecurityVerdict> verdicts) {
        List<SecurityCheckItemResponse> responses = new ArrayList<>();
        groups.forEach((key, group) -> {
            SecurityVerdict verdict = verdicts.get(representativeIdOf(group));
            VerdictLevel level = verdict == null ? VerdictLevel.NONE : verdict.getLevel();
            String guide = verdict == null ? null : verdict.getGuide();
            logVerdict(key, verdict, level);
            group.forEach(item -> {
                saveRiskLevel(item.getId(), level);
                responses.add(SecurityCheckItemResponse.of(item.getId(), item.getImageUrl(), item.getName(),
                        bagNameOf(bags.get(item.getBagId())), level.getLabel(), guide));
            });
        });
        return responses;
    }

    /**
     * 통과는 개수만 완료 로그에 남기고, 금지·주의와 판정이 오지 않은 묶음만 물품별로 남긴다.
     * "왜 이 물품이 걸렸나/안 걸렸나"를 나중에 따라갈 때 필요한 건 그 둘이다.
     */
    private void logVerdict(ItemGroup key, SecurityVerdict verdict, VerdictLevel level) {
        if (verdict == null) {
            log.warn("[security-check] 판정이 오지 않은 묶음은 통과로 둔다 물품={} 가방={} 구분={}",
                    key.name(), key.bagName(), key.carriageType());
            return;
        }
        if (level != VerdictLevel.NONE) {
            log.info("[security-check] 판정 물품={} 가방={} 구분={} → {}",
                    key.name(), key.bagName(), key.carriageType(), level.getLabel());
        }
    }

    /** 위험도만 남기고 안내 문구는 저장하지 않는다 — 마스터에 물품별 문구를 담을 자리가 없다. */
    private void saveRiskLevel(Long bagItemId, VerdictLevel level) {
        if (level == VerdictLevel.NONE) {
            bagItemRiskLevelRepository.deleteByBagItemIdAndStep(bagItemId, RiskCheckStep.SECURITY_CHECK);
            return;
        }
        Long riskLevelId = riskLevelIdOf(level.riskLevelType().orElseThrow());
        bagItemRiskLevelRepository.findByBagItemIdAndStep(bagItemId, RiskCheckStep.SECURITY_CHECK)
                .ifPresentOrElse(
                        existing -> existing.changeRiskLevel(riskLevelId),
                        () -> bagItemRiskLevelRepository.save(
                                BagItemRiskLevel.create(bagItemId, riskLevelId, RiskCheckStep.SECURITY_CHECK)));
    }

    private Long riskLevelIdOf(RiskLevelType type) {
        return riskLevelRepository.findByLevel(type)
                .map(RiskLevel::getId)
                .orElseThrow(() -> {
                    log.error("[security-check] RiskLevel 마스터에 {} 행이 없다 — RiskLevelInitializer 확인", type);
                    return new SecurityCheckFailedException();
                });
    }

    /** 응답은 금지 → 주의 → 통과, 같은 등급 안에서는 itemId 오름차순으로 정렬한다. */
    private SecurityCheckResponse summarize(List<SecurityCheckItemResponse> responses) {
        List<SecurityCheckItemResponse> sorted = responses.stream()
                .sorted(Comparator
                        .comparingInt((SecurityCheckItemResponse item) -> displayOrderOf(item.getRiskLevel()))
                        .thenComparing(SecurityCheckItemResponse::getItemId))
                .toList();

        int violationCount = countOf(sorted, VerdictLevel.VIOLATION);
        int warningCount = countOf(sorted, VerdictLevel.WARNING);
        int passCount = countOf(sorted, VerdictLevel.NONE);

        return SecurityCheckResponse.of(violationCount, warningCount, passCount, sorted);
    }

    private static int countOf(List<SecurityCheckItemResponse> items, VerdictLevel level) {
        return (int) items.stream().filter(item -> item.getRiskLevel().equals(level.getLabel())).count();
    }

    private static int displayOrderOf(String label) {
        for (VerdictLevel level : VerdictLevel.values()) {
            if (level.getLabel().equals(label)) {
                return level.displayOrder();
            }
        }
        return VerdictLevel.NONE.displayOrder();
    }

    /** 묶음의 대표는 가장 먼저 만들어진 물품 — 모델에게 이 ID를 보내고 그대로 돌려받는다. */
    private static Long representativeIdOf(List<BagItem> group) {
        return group.get(0).getId();
    }

    private static String bagNameOf(Bag bag) {
        return bag == null ? null : bag.getName();
    }

    private Trip findTripOwnedBy(Long tripId, Long memberId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        if (!trip.isOwnedBy(memberId)) {
            throw new TripAccessDeniedException("본인의 여행만 검사할 수 있습니다.");
        }
        return trip;
    }

    /** 질의 묶음의 키. 가방 이름·기내/위탁·물품 이름이 같으면 판정도 같다고 본다. */
    private record ItemGroup(String bagName, String carriageType, String name) {

        private static ItemGroup of(BagItem item, Bag bag) {
            String carriageType = bag == null || bag.getCarriageType() == null
                    ? SecurityCheckTarget.UNSPECIFIED
                    : bag.getCarriageType().getLabel();
            return new ItemGroup(bagNameOf(bag), carriageType, item.getName());
        }

        private SecurityCheckTarget toTarget(Long representativeItemId, String localTerms) {
            return new SecurityCheckTarget(representativeItemId, name, bagName, carriageType, localTerms);
        }
    }
}
