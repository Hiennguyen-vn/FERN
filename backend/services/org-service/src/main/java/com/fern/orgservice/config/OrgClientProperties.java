package com.fern.orgservice.config;

import com.fern.platform.web.FernDownstreamClientProperties;

public class OrgClientProperties {
    private FernDownstreamClientProperties pos = defaults("http://localhost:8086");
    private FernDownstreamClientProperties inventory = defaults("http://localhost:8087");
    private FernDownstreamClientProperties procurement = defaults("http://localhost:8088");
    private FernDownstreamClientProperties finance = defaults("http://localhost:8091");

    public FernDownstreamClientProperties getPos() {
        return pos;
    }

    public void setPos(FernDownstreamClientProperties pos) {
        this.pos = pos;
    }

    public FernDownstreamClientProperties getProcurement() {
        return procurement;
    }

    public void setProcurement(FernDownstreamClientProperties procurement) {
        this.procurement = procurement;
    }

    public FernDownstreamClientProperties getInventory() {
        return inventory;
    }

    public void setInventory(FernDownstreamClientProperties inventory) {
        this.inventory = inventory;
    }

    public FernDownstreamClientProperties getFinance() {
        return finance;
    }

    public void setFinance(FernDownstreamClientProperties finance) {
        this.finance = finance;
    }

    private FernDownstreamClientProperties defaults(String baseUrl) {
        FernDownstreamClientProperties properties = new FernDownstreamClientProperties();
        properties.setBaseUrl(baseUrl);
        return properties;
    }
}
