package com.company.incident.exception;

import com.company.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class IncidentNotFoundException extends BaseException {

    public IncidentNotFoundException(String incidentId) {
        super("Incident not found: " + incidentId, HttpStatus.NOT_FOUND);
    }
}
