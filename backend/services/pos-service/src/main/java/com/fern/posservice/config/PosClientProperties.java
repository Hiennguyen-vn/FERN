package com.fern.posservice.config;

import com.fern.platform.web.FernDownstreamClientProperties;

public class PosClientProperties {
    private FernDownstreamClientProperties catalog = defaults("http://localhost:8085");
    private FernDownstreamClientProperties inventory = defaults("http://localhost:8087");
    private FernDownstreamClientProperties org = defaults("http://localhost:8082");

    public FernDownstreamClientProperties getCatalog() {
        return catalog;
    }

    public void setCatalog(FernDownstreamClientProperties catalog) {
        this.catalog = catalog;
    }

    public FernDownstreamClientProperties getInventory() {
        return inventory;
    }

    public void setInventory(FernDownstreamClientProperties inventory) {
        this.inventory = inventory;
    }

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
