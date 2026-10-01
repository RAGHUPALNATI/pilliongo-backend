package com.raghu.pilliongo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Turns a place name ("Connaught Place, New Delhi") into a map position
// using OpenStreetMap's free Nominatim service. No API key needed, works
// worldwide. Nominatim's rules: max 1 request per second and a real
// User-Agent, so this is only called when an admin asks for it, never
// automatically on every ride.
@Slf4j
@Service
public class GeocodingService {

    private final RestClient http = RestClient.create();

    @Value("${app.geocoding.user-agent:PillionGo/1.0}")
    private String userAgent;

    // Optional comma-separated ISO country codes (e.g. "in") to bias
    // results. Empty = search the whole world.
    @Value("${app.geocoding.country-codes:}")
    private String countryCodes;

    public Optional<double[]> lookup(String placeName) {
        if (placeName == null || placeName.isBlank()) return Optional.empty();
        try {
            UriComponentsBuilder uri = UriComponentsBuilder
                    .fromUriString("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", placeName.trim())
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 1);
            if (countryCodes != null && !countryCodes.isBlank()) {
                uri.queryParam("countrycodes", countryCodes.trim());
            }
            List<Map<String, Object>> results = http.get()
                    .uri(uri.build().encode().toUri())
                    .header("User-Agent", userAgent)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (results == null || results.isEmpty()) return Optional.empty();
            Map<String, Object> first = results.get(0);
            double lat = Double.parseDouble(String.valueOf(first.get("lat")));
            double lng = Double.parseDouble(String.valueOf(first.get("lon")));
            return Optional.of(new double[]{lat, lng});
        } catch (Exception e) {
            log.warn("Geocoding failed for '{}': {}", placeName, e.getMessage());
            return Optional.empty();
        }
    }
}
