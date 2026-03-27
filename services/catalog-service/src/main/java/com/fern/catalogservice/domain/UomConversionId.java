package com.fern.catalogservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UomConversionId implements Serializable {
    @Column(nullable = false)
    private String fromUomCode;

    @Column(nullable = false)
    private String toUomCode;

    public UomConversionId() {
    }

    public UomConversionId(String fromUomCode, String toUomCode) {
        this.fromUomCode = fromUomCode;
        this.toUomCode = toUomCode;
    }

    public String getFromUomCode() {
        return fromUomCode;
    }

    public void setFromUomCode(String fromUomCode) {
        this.fromUomCode = fromUomCode;
    }

    public String getToUomCode() {
        return toUomCode;
    }

    public void setToUomCode(String toUomCode) {
        this.toUomCode = toUomCode;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UomConversionId that)) {
            return false;
        }
        return Objects.equals(fromUomCode, that.fromUomCode) && Objects.equals(toUomCode, that.toUomCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromUomCode, toUomCode);
    }
}
