package com.company.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

@RestController
@RequestMapping("/fallback")
class FallbackController {

    @GetMapping("/incident-service")
    public ResponseEntity<Map<String, String>> incidentFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "incident-service is currently unavailable",
                        "message", "Please retry in a moment. Our team has been alerted."
                ));
    }

    @GetMapping("/copilot-service")
    public ResponseEntity<Map<String, String>> copilotFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "copilot-service is currently unavailable",
                        "message", "AI analysis is temporarily offline. Manual triage is required."
                ));
    }
}
