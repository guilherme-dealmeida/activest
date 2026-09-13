CREATE TABLE asset_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    color_hex VARCHAR(7)
);
