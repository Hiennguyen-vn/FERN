package com.fern.reportservice.config;

import com.fern.platform.web.FernDownstreamClientProperties;

public class ReportClientProperties {
    private FernDownstreamClientProperties pos = defaults("http://localhost:8086");
    private FernDownstreamClientProperties inventory = defaults("http://localhost:8087");

    public FernDownstreamClientProperties getPos() {
        return pos;
    }

    public void setPos(FernDownstreamClientProperties pos) {
        this.pos = pos;
    }

    public FernDownstreamClientProperties getInventory() {
        return inventory;
    }

    public void setInventory(FernDownstreamClientProperties inventory) {
        this.inventory = inventory;
    }

    private FernDownstreamClientProperties defaults(String baseUrl) {
        FernDownstreamClientProperties properties = new FernDownstreamClientProperties();
        properties.setBaseUrl(baseUrl);
        return properties;
    }
}
