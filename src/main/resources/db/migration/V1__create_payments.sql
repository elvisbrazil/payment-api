create table payments (
    id                 uuid primary key,
    amount             numeric(19, 4) not null,
    currency           varchar(3)     not null,
    refunded_amount    numeric(19, 4) not null,
    status             varchar(32)    not null,
    card_last4         varchar(4)     not null,
    authorization_code varchar(64),
    decline_reason     varchar(255),
    created_at         timestamp with time zone not null,
    updated_at         timestamp with time zone not null,
    version            bigint         not null
);

create table refunds (
    id         uuid primary key,
    payment_id uuid           not null references payments (id),
    amount     numeric(19, 4) not null,
    created_at timestamp with time zone not null
);

create index idx_refunds_payment_id on refunds (payment_id);
