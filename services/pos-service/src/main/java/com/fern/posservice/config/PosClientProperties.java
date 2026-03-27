package com.fern.posservice.config;

public class PosClientProperties {
    private String catalogBaseUrl = "http://localhost:8085";
    private String inventoryBaseUrl = "http://localhost:8087";

    public String getCatalogBaseUrl() {
        return catalogBaseUrl;
    }

    public void setCatalogBaseUrl(String catalogBaseUrl) {
        this.catalogBaseUrl = catalogBaseUrl;
    }

    public String getInventoryBaseUrl() {
        return inventoryBaseUrl;
    }

    public void setInventoryBaseUrl(String inventoryBaseUrl) {
        this.inventoryBaseUrl = inventoryBaseUrl;
    }
}
