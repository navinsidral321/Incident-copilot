package com.company.incident.service;

import com.company.incident.dto.IncidentDto;
import com.company.incident.model.Incident;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface IncidentMapper {

    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "status",    ignore = true)
    @Mapping(target = "aiRootCause",          ignore = true)
    @Mapping(target = "aiRecommendedActions", ignore = true)
    @Mapping(target = "aiRunbook",            ignore = true)
    @Mapping(target = "aiSummary",            ignore = true)
    @Mapping(target = "resolvedAt",           ignore = true)
    @Mapping(target = "timeToResolveMinutes", ignore = true)
    @Mapping(target = "createdAt",            ignore = true)
    @Mapping(target = "updatedAt",            ignore = true)
    @Mapping(target = "version",              ignore = true)
    Incident toEntity(IncidentDto.CreateRequest request);

    @Mapping(target = "aiAnalysisAvailable",
             expression = "java(incident.getAiRootCause() != null)")
    IncidentDto.Response toResponse(Incident incident);

    IncidentDto.SummaryResponse toSummary(Incident incident);
}
