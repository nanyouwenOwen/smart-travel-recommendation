package com.travelassistant.trip;

import com.travelassistant.common.api.ApiMeta;
import com.travelassistant.common.api.ApiResponse;
import com.travelassistant.common.web.RequestIdFilter;
import com.travelassistant.trip.api.AdjustTripRequest;
import com.travelassistant.trip.api.CreateTripRequest;
import com.travelassistant.trip.api.TripDtos.PageResult;
import com.travelassistant.trip.api.TripDtos.TripSummary;
import com.travelassistant.trip.api.TripDtos.TripView;
import com.travelassistant.trip.api.TripDtos.VersionSummary;
import com.travelassistant.trip.api.TripDtos.VersionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {
    private final TripCommandService commands;
    private final TripQueryService queries;

    public TripController(TripCommandService commands, TripQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    ResponseEntity<ApiResponse<TripView>> create(
            Principal principal,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128) String idempotencyKey,
            @Valid @RequestBody CreateTripRequest body,
            HttpServletRequest request) {
        Trip trip = commands.create(principal.getName(), idempotencyKey, body);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(queries.get(principal.getName(), trip.getId()), requestId(request)));
    }

    @GetMapping
    TripListResponse list(
            Principal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        PageResult<TripSummary> page = queries.list(principal.getName(), cursor, limit);
        return new TripListResponse(page.items(),
                new TripListMeta(requestId(request), page.nextCursor(), page.hasMore()));
    }

    @GetMapping("/{tripId}")
    ApiResponse<TripView> get(Principal principal, @PathVariable String tripId,
                              HttpServletRequest request) {
        return ApiResponse.of(queries.get(principal.getName(), tripId), requestId(request));
    }

    @PatchMapping("/{tripId}")
    ResponseEntity<ApiResponse<TripView>> update(
            Principal principal, @PathVariable String tripId,
            @Valid @RequestBody CreateTripRequest body, HttpServletRequest request) {
        commands.update(principal.getName(), tripId, body);
        return ResponseEntity.accepted()
                .body(ApiResponse.of(queries.get(principal.getName(), tripId), requestId(request)));
    }

    @DeleteMapping("/{tripId}")
    ResponseEntity<Void> delete(Principal principal, @PathVariable String tripId) {
        commands.delete(principal.getName(), tripId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tripId}/adjustments")
    ResponseEntity<ApiResponse<TripView>> adjust(
            Principal principal, @PathVariable String tripId,
            @Valid @RequestBody AdjustTripRequest body, HttpServletRequest request) {
        commands.adjust(principal.getName(), tripId, body.instruction());
        return ResponseEntity.accepted()
                .body(ApiResponse.of(queries.get(principal.getName(), tripId), requestId(request)));
    }

    @GetMapping("/{tripId}/versions")
    ApiResponse<List<VersionSummary>> versions(
            Principal principal, @PathVariable String tripId, HttpServletRequest request) {
        return ApiResponse.of(queries.versions(principal.getName(), tripId), requestId(request));
    }

    @GetMapping("/{tripId}/versions/{versionNumber}")
    ApiResponse<VersionView> version(
            Principal principal, @PathVariable String tripId,
            @PathVariable @Min(1) int versionNumber, HttpServletRequest request) {
        return ApiResponse.of(queries.version(principal.getName(), tripId, versionNumber), requestId(request));
    }

    @PostMapping("/{tripId}/versions/{versionNumber}:restore")
    ApiResponse<TripView> restore(
            Principal principal, @PathVariable String tripId,
            @PathVariable @Min(1) int versionNumber, HttpServletRequest request) {
        commands.restore(principal.getName(), tripId, versionNumber);
        return ApiResponse.of(queries.get(principal.getName(), tripId), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }

    record TripListResponse(List<TripSummary> data, TripListMeta meta) {}
    record TripListMeta(String requestId, String nextCursor, boolean hasMore) {}
}
