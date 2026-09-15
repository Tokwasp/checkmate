package ssafy.checkmate.domain.trip.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.trip.dto.request.TripCreateRequest;
import ssafy.checkmate.domain.trip.dto.response.PackingProgressResponse;
import ssafy.checkmate.domain.trip.dto.response.PlaceSuggestionResponse;
import ssafy.checkmate.domain.trip.dto.response.TripCreateResponse;
import ssafy.checkmate.domain.trip.dto.response.TripListResponse;
import ssafy.checkmate.domain.trip.service.TripService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @GetMapping("/api/v1/trips/destinations/autocomplete")
    public ResponseEntity<ApiResponse<List<PlaceSuggestionResponse>>> autocompleteDestination(
            @LoginMember MemberPrincipal principal,
            @RequestParam String query, @RequestParam String sessionToken) {
        return ResponseEntity.ok(ApiResponse.success(tripService.autocompleteDestination(query, sessionToken)));
    }

    @PostMapping("/api/v1/trips")
    public ResponseEntity<ApiResponse<TripCreateResponse>> create(
            @LoginMember MemberPrincipal principal, @Valid @RequestBody TripCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tripService.createTrip(principal.memberId(), request)));
    }

    @GetMapping("/api/v1/trips")
    public ResponseEntity<ApiResponse<TripListResponse>> list(@LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(tripService.getTrips(principal.memberId(), LocalDate.now())));
    }

    @PutMapping("/api/v1/trips/{tripId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable Long tripId, @LoginMember MemberPrincipal principal,
            @Valid @RequestBody TripCreateRequest request) {
        tripService.updateTrip(tripId, principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/api/v1/trips/{tripId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long tripId, @LoginMember MemberPrincipal principal) {
        tripService.deleteTrip(tripId, principal.memberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/api/v1/trips/{tripId}/packing-session")
    public ResponseEntity<ApiResponse<PackingProgressResponse>> packingSession(
            @PathVariable Long tripId, @LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(tripService.getPackingSession(tripId, principal.memberId())));
    }
}
