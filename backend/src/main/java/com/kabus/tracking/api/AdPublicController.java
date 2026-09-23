package com.kabus.tracking.api;

import com.kabus.tracking.service.AdService;
import com.kabus.tracking.web.dto.AdDtos;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public ad serving + impression recording. Impressions are only recorded
 * when a client reports that an ad was actually displayed; the system never
 * fabricates them.
 */
@RestController
@RequestMapping("/api/public/ad")
public class AdPublicController {

    private final AdService adService;

    public AdPublicController(AdService adService) {
        this.adService = adService;
    }

    @GetMapping("/serve")
    public ResponseEntity<AdDtos.ServeAdResponse> serve(@RequestParam String placement) {
        return ResponseEntity.ok(adService.serve(placement));
    }

    @PostMapping("/impression")
    public ResponseEntity<Void> impression(@Valid @RequestBody AdDtos.ImpressionRequest request) {
        adService.recordImpression(request);
        return ResponseEntity.noContent().build();
    }
}