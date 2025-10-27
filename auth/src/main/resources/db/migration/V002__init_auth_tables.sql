create table if not exists auth_user (
    id            bigserial primary key,
    email         varchar(255) unique not null,
    password_hash varchar(255) not null,
    enabled       boolean not null default true,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create table if not exists auth_role (
    id   bigserial primary key,
    name varchar(50) unique not null
);

create table if not exists auth_user_role (
    user_id bigint not null references auth_user(id) on delete cascade,
    role_id bigint not null references auth_role(id) on delete cascade,
    primary key (user_id, role_id)
);

create table if not exists auth_session (
    id              uuid primary key,
    user_id         bigint not null references auth_user(id) on delete cascade,
    created_at      timestamptz not null default now(),
    last_seen_at    timestamptz not null default now(),
    expires_at      timestamptz not null,
    ip_address      varchar(64),
    user_agent      varchar(255),
    revoked         boolean not null default false
);

create index if not exists idx_auth_session_user on auth_session(user_id);
create index if not exists idx_auth_session_expires on auth_session(expires_at);