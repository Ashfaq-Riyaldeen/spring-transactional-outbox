--liquibase formatted sql

--changeset order-service:001-orders
CREATE TABLE orders (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id  BIGINT         NOT NULL,
    status       VARCHAR(16)    NOT NULL,
    total        NUMERIC(19, 2) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT orders_status_check CHECK (status IN ('CREATED', 'CANCELLED')),
    CONSTRAINT orders_total_check CHECK (total >= 0)
);

CREATE INDEX orders_customer_id_idx ON orders (customer_id);

CREATE TABLE order_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id),
    product_id VARCHAR(64)    NOT NULL,
    quantity   INT            NOT NULL,
    price      NUMERIC(19, 2) NOT NULL,
    CONSTRAINT order_items_quantity_check CHECK (quantity > 0),
    CONSTRAINT order_items_price_check CHECK (price >= 0)
);

CREATE INDEX order_items_order_id_idx ON order_items (order_id);
