package com.ploa.briefing.api;

import com.ploa.briefing.BriefingOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/briefing")
public class BriefingController {

    private final BriefingOrchestrator orchestrator;

    public BriefingController(BriefingOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Preview today's AI morning briefing text without sending it to Telegram.
     */
    @GetMapping("/preview")
    public ResponseEntity<Map<String, String>> preview() {
        String briefing = orchestrator.generateBriefingPreview();
        return ResponseEntity.ok(Map.of("briefing", briefing));
    }

    /**
     * Manually trigger morning briefing synthesis and push to Telegram.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> trigger() {
        orchestrator.sendMorningBriefing();
        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    /**
     * Preview today's AI evening summary text without sending it to Telegram.
     */
    @GetMapping("/evening/preview")
    public ResponseEntity<Map<String, String>> previewEvening() {
        String summary = orchestrator.generateEveningSummaryPreview();
        return ResponseEntity.ok(Map.of("summary", summary));
    }

    /**
     * Manually trigger evening summary synthesis and push to Telegram.
     */
    @PostMapping("/evening/trigger")
    public ResponseEntity<Map<String, String>> triggerEvening() {
        orchestrator.sendEveningSummary();
        return ResponseEntity.ok(Map.of("status", "sent"));
    }
}
