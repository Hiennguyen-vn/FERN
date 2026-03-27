package com.fern.posservice.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PosReferenceCodeGenerator {
    public String nextSessionCode() {
        return "POSS-" + UUID.randomUUID();
    }

    public String nextOrderNumber() {
        return "SO-" + UUID.randomUUID();
    }
}
