package com.planmate.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.planmate.dto.LocationSuggestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LocationService {

	@Value("${locationiq.api.key}")
	private String apiKey;

	private final RestTemplate restTemplate = new RestTemplate();

	public List<LocationSuggestion> autocomplete(String query) {
		String url = UriComponentsBuilder.fromUriString("https://api.locationiq.com/v1/autocomplete")
				.queryParam("key", apiKey).queryParam("q", query).queryParam("limit", 5).queryParam("dedupe", 1)
				.toUriString();

		try {
			List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);
			List<LocationSuggestion> suggestions = new ArrayList<>();

			if (response != null) {
				for (Map<String, Object> item : response) {
					LocationSuggestion dto = new LocationSuggestion();
					dto.setPlaceId((String) item.get("place_id"));
					dto.setDisplayName((String) item.get("display_name"));
					dto.setLat(Double.parseDouble((String) item.get("lat")));
					dto.setLon(Double.parseDouble((String) item.get("lon")));

					Map<String, String> address = (Map<String, String>) item.get("address");
					if (address != null) {
						dto.setStreet(address.get("road") != null
								? address.get("road")
										+ (address.get("house_number") != null ? " " + address.get("house_number") : "")
								: address.get("name"));
						dto.setCity(address.get("city") != null ? address.get("city") : address.get("town"));
						dto.setZipcode(address.get("postcode"));
						dto.setCountry(address.get("country"));
					}

					suggestions.add(dto);
				}
			}
			return suggestions;
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}
}
