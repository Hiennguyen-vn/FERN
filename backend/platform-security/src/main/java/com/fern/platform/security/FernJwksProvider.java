package com.fern.platform.security;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;

public class FernJwksProvider {
    private final JWKSet jwkSet;

    public FernJwksProvider(JWKSet jwkSet) {
        this.jwkSet = jwkSet;
    }

    public JWKSet currentJwkSet() {
        return jwkSet;
    }

    public Map<String, Object> currentJwkSetPayload() {
        return jwkSet.toJSONObject();
    }
}
