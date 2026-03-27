CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE IF NOT EXISTS catalog.product_category (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS catalog.ingredient_category (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS catalog.unit_of_measure (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    symbol VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS catalog.uom_conversion (
    from_uom_code VARCHAR(30) NOT NULL REFERENCES catalog.unit_of_measure(code),
    to_uom_code VARCHAR(30) NOT NULL REFERENCES catalog.unit_of_measure(code),
    conversion_factor NUMERIC(20, 8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_uom_code, to_uom_code),
    CONSTRAINT catalog_uom_conversion_ck CHECK (
        conversion_factor > 0 AND from_uom_code <> to_uom_code
    )
);

CREATE TABLE IF NOT EXISTS catalog.ingredient (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    category_code VARCHAR(50) REFERENCES catalog.ingredient_category(code),
    base_uom_code VARCHAR(30) NOT NULL REFERENCES catalog.unit_of_measure(code),
    min_stock_level NUMERIC(18, 4),
    max_stock_level NUMERIC(18, 4),
    status VARCHAR(20) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_by_user_id BIGINT,
    updated_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_ingredient_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED')),
    CONSTRAINT catalog_ingredient_stock_ck CHECK (
        (min_stock_level IS NULL OR min_stock_level >= 0) AND
        (max_stock_level IS NULL OR max_stock_level >= 0) AND
        (min_stock_level IS NULL OR max_stock_level IS NULL OR max_stock_level >= min_stock_level)
    )
);

CREATE TABLE IF NOT EXISTS catalog.product (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    category_code VARCHAR(50) REFERENCES catalog.product_category(code),
    status VARCHAR(20) NOT NULL,
    image_url TEXT,
    description TEXT,
    deleted_at TIMESTAMPTZ,
    created_by_user_id BIGINT,
    updated_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_product_status_ck CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'DISCONTINUED'))
);

CREATE TABLE IF NOT EXISTS catalog.recipe (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES catalog.product(id),
    recipe_code VARCHAR(50) NOT NULL,
    description TEXT,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_recipe_product_uk UNIQUE (product_id),
    CONSTRAINT catalog_recipe_code_uk UNIQUE (recipe_code)
);

CREATE TABLE IF NOT EXISTS catalog.recipe_version (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES catalog.recipe(id) ON DELETE CASCADE,
    version_no VARCHAR(30) NOT NULL,
    yield_qty NUMERIC(18, 4) NOT NULL,
    yield_uom_code VARCHAR(30) NOT NULL REFERENCES catalog.unit_of_measure(code),
    status VARCHAR(20) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_recipe_version_uk UNIQUE (recipe_id, version_no),
    CONSTRAINT catalog_recipe_version_status_ck CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT catalog_recipe_version_dates_ck CHECK (yield_qty > 0 AND (effective_to IS NULL OR effective_to >= effective_from))
);

CREATE TABLE IF NOT EXISTS catalog.recipe_version_ingredient (
    id BIGSERIAL PRIMARY KEY,
    recipe_version_id BIGINT NOT NULL REFERENCES catalog.recipe_version(id) ON DELETE CASCADE,
    ingredient_id BIGINT NOT NULL REFERENCES catalog.ingredient(id),
    uom_code VARCHAR(30) NOT NULL REFERENCES catalog.unit_of_measure(code),
    qty NUMERIC(18, 4) NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_recipe_version_ingredient_uk UNIQUE (recipe_version_id, ingredient_id),
    CONSTRAINT catalog_recipe_version_ingredient_qty_ck CHECK (qty > 0)
);

CREATE TABLE IF NOT EXISTS catalog.tax_rate (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES catalog.product(id),
    tax_percent NUMERIC(5, 2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_tax_rate_uk UNIQUE (product_id, effective_from),
    CONSTRAINT catalog_tax_rate_ck CHECK (tax_percent >= 0 AND tax_percent <= 100 AND (effective_to IS NULL OR effective_to >= effective_from))
);

CREATE TABLE IF NOT EXISTS catalog.product_price (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES catalog.product(id),
    scope_type VARCHAR(20) NOT NULL,
    scope_id BIGINT,
    price_type VARCHAR(20) NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    price_value NUMERIC(18, 2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_by_user_id BIGINT,
    updated_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_product_price_uk UNIQUE (product_id, scope_type, scope_id, price_type, effective_from),
    CONSTRAINT catalog_product_price_scope_ck CHECK (scope_type IN ('GLOBAL', 'COUNTRY', 'REGION', 'OUTLET')),
    CONSTRAINT catalog_product_price_type_ck CHECK (price_type IN ('RETAIL', 'DINE_IN', 'TAKEAWAY', 'DELIVERY', 'WHOLESALE')),
    CONSTRAINT catalog_product_price_dates_ck CHECK (price_value >= 0 AND (effective_to IS NULL OR effective_to >= effective_from))
);

CREATE TABLE IF NOT EXISTS catalog.product_outlet_availability (
    product_id BIGINT NOT NULL REFERENCES catalog.product(id),
    outlet_id BIGINT NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, outlet_id)
);

CREATE TABLE IF NOT EXISTS catalog.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT catalog_outbox_status_ck CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_catalog_ingredient_code_active
    ON catalog.ingredient (code) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_catalog_product_code_active
    ON catalog.product (code) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_catalog_ingredient_category ON catalog.ingredient (category_code);
CREATE INDEX IF NOT EXISTS idx_catalog_product_category ON catalog.product (category_code);
CREATE INDEX IF NOT EXISTS idx_catalog_product_status ON catalog.product (status);
CREATE INDEX IF NOT EXISTS idx_catalog_price_scope ON catalog.product_price (scope_type, scope_id, effective_from);
CREATE INDEX IF NOT EXISTS idx_catalog_availability_outlet ON catalog.product_outlet_availability (outlet_id);
