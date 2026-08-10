package com.planmate.controller;


import com.planmate.dto.LocationSuggestion;
import com.planmate.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    @Autowired
    private LocationService locationService;

    @GetMapping("/autocomplete")
    public ResponseEntity<List<LocationSuggestion>> getAutocompleteSuggestions(@RequestParam String q) {
        if (q == null || q.trim().length() < 3) {
            return ResponseEntity.ok(List.of());
        }
        List<LocationSuggestion> suggestions = locationService.autocomplete(q);
        return ResponseEntity.ok(suggestions);
    }
}