package com.fern.financeservice.config;

import com.fern.platform.web.FernDownstreamClientProperties;

public class FinanceClientProperties {
    private FernDownstreamClientProperties hr = defaults("http://localhost:8089");

    public FernDownstreamClientProperties getHr() {
        return hr;
    }

    public void setHr(FernDownstreamClientProperties hr) {
        this.hr = hr;
    }

    private FernDownstreamClientProperties defaults(String baseUrl) {
        FernDownstreamClientProperties properties = new FernDownstreamClientProperties();
        properties.setBaseUrl(baseUrl);
        return properties;
    }
}
