package com.fern.iamservice.controller;

import com.fern.platform.security.FernJwksProvider;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityMetadataController {
    private final FernJwksProvider jwksProvider;

    public SecurityMetadataController(FernJwksProvider jwksProvider) {
        this.jwksProvider = jwksProvider;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> publicJwks() {
        return jwksProvider.currentJwkSetPayload();
    }

    @GetMapping(value = "/internal/security/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> internalJwks() {
        return jwksProvider.currentJwkSetPayload();
    }
}
