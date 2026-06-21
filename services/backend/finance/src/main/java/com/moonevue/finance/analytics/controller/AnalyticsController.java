package com.moonevue.finance.analytics.controller;

import com.moonevue.core.security.IntrospectedAuthToken;
import com.moonevue.finance.analytics.domain.Granularity;
import com.moonevue.finance.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * API de Analytics Corporativo. Todos os endpoints são tenant-scoped e exigem sessão
 * válida (validada pelo {@code SessionValidationFilter} do Finance).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/{tenantId}/analytics")
public class AnalyticsController {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_TOP_CLIENTS = 100;

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@PathVariable("tenantId") Long tenantId,
                                       @RequestParam(value = "from", required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(value = "to", required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(value = "granularity", required = false) Granularity granularity,
                                       @RequestParam(value = "topClients", defaultValue = "10") int topClients,
                                       Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return forbidden();
        }
        DateRange range = resolveRange(from, to);
        return ResponseEntity.ok(analyticsService.getDashboard(
                tenantId, range.from(), range.to(), resolveGranularity(granularity),
                clampTopClients(topClients)));
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@PathVariable("tenantId") Long tenantId,
                                     @RequestParam(value = "from", required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(value = "to", required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(value = "granularity", required = false) Granularity granularity,
                                     Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return forbidden();
        }
        DateRange range = resolveRange(from, to);
        return ResponseEntity.ok(analyticsService.getSummary(
                tenantId, range.from(), range.to(), resolveGranularity(granularity)));
    }

    @GetMapping("/revenue/timeseries")
    public ResponseEntity<?> revenueTimeSeries(@PathVariable("tenantId") Long tenantId,
                                               @RequestParam(value = "from", required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(value = "to", required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(value = "granularity", required = false) Granularity granularity,
                                               Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return forbidden();
        }
        DateRange range = resolveRange(from, to);
        return ResponseEntity.ok(analyticsService.getRevenueTimeSeries(
                tenantId, range.from(), range.to(), resolveGranularity(granularity)));
    }

    @GetMapping("/clients/ranking")
    public ResponseEntity<?> clientRanking(@PathVariable("tenantId") Long tenantId,
                                           @RequestParam(value = "from", required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(value = "to", required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(value = "topClients", defaultValue = "10") int topClients,
                                           Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return forbidden();
        }
        DateRange range = resolveRange(from, to);
        return ResponseEntity.ok(analyticsService.getClientRanking(
                tenantId, range.from(), range.to(), clampTopClients(topClients)));
    }

    @GetMapping("/status-breakdown")
    public ResponseEntity<?> statusBreakdown(@PathVariable("tenantId") Long tenantId,
                                             @RequestParam(value = "from", required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(value = "to", required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return forbidden();
        }
        DateRange range = resolveRange(from, to);
        return ResponseEntity.ok(analyticsService.getStatusBreakdown(tenantId, range.from(), range.to()));
    }

    @GetMapping("/receivables")
    public ResponseEntity<?> receivables(@PathVariable("tenantId") Long tenantId, Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return forbidden();
        }
        return ResponseEntity.ok(analyticsService.getReceivables(tenantId));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS);
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return new DateRange(start, end);
    }

    private Granularity resolveGranularity(Granularity granularity) {
        return granularity != null ? granularity : Granularity.DAY;
    }

    private int clampTopClients(int topClients) {
        if (topClients < 1) {
            return 1;
        }
        return Math.min(topClients, MAX_TOP_CLIENTS);
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acesso negado ao tenant"));
    }

    private boolean isAuthorizedForTenant(Authentication auth, Long tenantId) {
        if (!(auth instanceof IntrospectedAuthToken token)) {
            return false;
        }
        Object details = token.getDetails();
        if (!(details instanceof Map<?, ?> map)) {
            return false;
        }
        Object tid = map.get("tenantId");
        return tid instanceof Number n && n.longValue() == tenantId;
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
