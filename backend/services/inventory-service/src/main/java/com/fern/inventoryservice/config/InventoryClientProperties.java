package com.fern.inventoryservice.config;

import com.fern.platform.web.FernDownstreamClientProperties;

public class InventoryClientProperties {
    private FernDownstreamClientProperties org = defaults("http://localhost:8082");

    public FernDownstreamClientProperties getOrg() {
        return org;
    }

    public void setOrg(FernDownstreamClientProperties org) {
        this.org = org;
    }

    private FernDownstreamClientProperties defaults(String baseUrl) {
        FernDownstreamClientProperties properties = new FernDownstreamClientProperties();
        properties.setBaseUrl(baseUrl);
        return properties;
    }
}
