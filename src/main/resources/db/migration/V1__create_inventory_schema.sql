CREATE TABLE products (
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku         VARCHAR(64) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_sku_not_blank CHECK (length(btrim(sku)) > 0),
    CONSTRAINT ck_products_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE inventory_balances (
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  INT NOT NULL,
    quantity    INT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_inventory_balances_product UNIQUE (product_id),
    CONSTRAINT fk_inventory_balances_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_inventory_balances_quantity CHECK (quantity >= 0)
);

CREATE TABLE inventory_movements (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inventory_balance_id  INT NOT NULL,
    movement_type         VARCHAR(16) NOT NULL,
    quantity              INT NOT NULL,
    balance_after         INT NOT NULL,
    reason                VARCHAR(500),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT fk_inventory_movements_balance
        FOREIGN KEY (inventory_balance_id) REFERENCES inventory_balances (id),
    CONSTRAINT ck_inventory_movements_type
        CHECK (movement_type IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_inventory_movements_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_movements_balance_after CHECK (balance_after >= 0)
);

CREATE INDEX idx_inventory_movements_balance_created
    ON inventory_movements (inventory_balance_id, created_at DESC, id DESC);
