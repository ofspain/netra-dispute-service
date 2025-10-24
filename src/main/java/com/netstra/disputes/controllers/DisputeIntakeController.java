package com.netstra.disputes.controllers;

import com.netra.commons.requests.CreateDisputeRequest;
import com.netstra.disputes.security.DomainAwarePrincipal;
import com.netstra.disputes.services.DisputeIntakeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeIntakeController {

    private final DisputeIntakeService disputeIntakeService;

    @PostMapping("/create/single")
    public ResponseEntity<?> intakeDispute(
            @Valid @RequestBody CreateDisputeRequest request,
            @AuthenticationPrincipal DomainAwarePrincipal userPrincipal) {

        var response = disputeIntakeService.createDispute(request, userPrincipal, true);
        return ResponseEntity.ok(response);
    }
}

