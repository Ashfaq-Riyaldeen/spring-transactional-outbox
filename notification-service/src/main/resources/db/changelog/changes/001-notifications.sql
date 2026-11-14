--liquibase formatted sql

--changeset notification-service:001-notifications
CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    UUID        NOT NULL,
    order_id    BIGINT      NOT NULL,
    customer_id BIGINT      NOT NULL,
    type        VARCHAR(32) NOT NULL,
    channel     VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT notifications_type_check CHECK (type IN ('ORDER_CREATED', 'ORDER_CANCELLED')),
    CONSTRAINT notifications_channel_check CHECK (channel IN ('LOG'))
);

CREATE INDEX notifications_order_id_idx ON notifications (order_id);
