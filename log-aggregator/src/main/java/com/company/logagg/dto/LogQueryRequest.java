package com.company.logagg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Internal DTO used by LogSourceClient when calling
 * Loki or Elasticsearch to fetch logs for a service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogQueryRequest {

    private String  serviceName;
    private Instant from;
    private Instant to;

    @Builder.Default
    private int     maxEntries = 500;

    @Builder.Default
    private String  minLevel = "WARN";   // WARN | ERROR | INFO
}
