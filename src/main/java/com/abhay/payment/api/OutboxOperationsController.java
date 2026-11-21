package com.abhay.payment.api;

import com.abhay.payment.infrastructure.persistence.OutboxReplayService;
import com.abhay.payment.infrastructure.security.MerchantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outbox")
public class OutboxOperationsController {
    private final OutboxReplayService replay;
    public OutboxOperationsController(OutboxReplayService replay) { this.replay = replay; }

    @PostMapping("/{eventId}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> replay(@PathVariable UUID eventId,
            @AuthenticationPrincipal MerchantPrincipal principal) {
        replay.replay(principal.merchantId(), eventId);
        return Map.of("eventId", eventId.toString(), "status", "REQUEUED");
    }
}
