package com.airtribe.prism.cache.controller;

import com.airtribe.prism.cache.dto.CacheDtos;
import com.airtribe.prism.cache.service.SemanticCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SemanticCacheController {

    private final SemanticCacheService semanticCacheService;

    @PostMapping("/cache/lookup")
    public CacheDtos.LookupResponse lookup(@Valid @RequestBody CacheDtos.LookupRequest request) {
        return semanticCacheService.lookup(request);
    }

    @PostMapping("/cache/store")
    public ResponseEntity<Void> store(@Valid @RequestBody CacheDtos.StoreRequest request) {
        semanticCacheService.store(request);
        return ResponseEntity.accepted().build();
    }
}
