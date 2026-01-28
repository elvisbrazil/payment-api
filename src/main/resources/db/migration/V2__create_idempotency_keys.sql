create table idempotency_keys (
    idempotency_key varchar(255) primary key,
    request_hash    varchar(64)  not null,
    response_body   varchar      not null,
    created_at      timestamp with time zone not null
);
