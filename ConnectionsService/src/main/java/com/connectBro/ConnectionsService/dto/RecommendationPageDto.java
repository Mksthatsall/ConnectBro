package com.connectBro.ConnectionsService.dto;

import java.util.List;

public record RecommendationPageDto(List<RecommendationDto> items, int page, int size, long total) { }
