package com.fern.apigateway.config;

import jakarta.annotation.PostConstruct;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fern.routes")
public class GatewayRoutesProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRoutesProperties.class);

    @PostConstruct
    void validateRoutes() {
        String activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfiles != null && activeProfiles.contains("prod")) {
            Stream.of(iam, org, catalog, audit, pos, inventory, procurement, hr, report, finance)
                    .filter(uri -> uri != null && uri.startsWith("http://"))
                    .findFirst()
                    .ifPresent(uri -> {
                        throw new IllegalStateException(
                                "Inter-service routes must use HTTPS in production. Found: " + uri);
                    });
        } else {
            Stream.of(iam, org, catalog, audit, pos, inventory, procurement, hr, report, finance)
                    .filter(uri -> uri != null && uri.startsWith("http://"))
                    .findFirst()
                    .ifPresent(uri -> LOGGER.warn("Inter-service routes use HTTP — ensure HTTPS in production"));
        }
    }

    private String iam = "http://localhost:8081";
    private String org = "http://localhost:8082";
    private String catalog = "http://localhost:8085";
    private String audit = "http://localhost:8084";
    private String pos = "http://localhost:8086";
    private String inventory = "http://localhost:8087";
    private String procurement = "http://localhost:8088";
    private String hr = "http://localhost:8089";
    private String report = "http://localhost:8090";
    private String finance = "http://localhost:8091";

    public String getIam() {
        return iam;
    }

    public void setIam(String iam) {
        this.iam = iam;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getAudit() {
        return audit;
    }

    public void setAudit(String audit) {
        this.audit = audit;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    public String getInventory() {
        return inventory;
    }

    public void setInventory(String inventory) {
        this.inventory = inventory;
    }

    public String getProcurement() {
        return procurement;
    }

    public void setProcurement(String procurement) {
        this.procurement = procurement;
    }

    public String getHr() {
        return hr;
    }

    public void setHr(String hr) {
        this.hr = hr;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public String getFinance() {
        return finance;
    }

    public void setFinance(String finance) {
        this.finance = finance;
    }
}
