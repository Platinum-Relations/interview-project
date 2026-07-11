create table import_batch (
                              id bigint generated always as identity primary key,
                              dataset_name varchar(100) not null,
                              source_type varchar(50) not null,
                              source_path varchar(500) not null,
                              imported_at timestamp with time zone not null default current_timestamp,
                              valid_row_count integer not null default 0,
                              quarantined_row_count integer not null default 0
);

create table internal_transaction (
                                      internal_txn_id varchar(64) primary key,
                                      import_batch_id bigint not null,
                                      merchant_id varchar(64) not null,
                                      merchant_ref varchar(128) not null,
                                      card_type varchar(32) not null,
                                      card_last4 varchar(4) not null,
                                      gross_amount decimal(19, 4) not null,
                                      currency varchar(3) not null,
                                      transaction_type varchar(16) not null,
                                      captured_at timestamp with time zone not null,

                                      constraint fk_internal_transaction_import_batch
                                          foreign key (import_batch_id) references import_batch (id),

                                      constraint chk_internal_transaction_type
                                          check (transaction_type in ('SALE', 'REFUND')),

                                      constraint chk_internal_currency
                                          check (currency = 'USD')
);

create table settlement_record (
                                   network_ref varchar(64) primary key,
                                   import_batch_id bigint not null,
                                   merchant_ref varchar(128),
                                   merchant_id varchar(64) not null,
                                   card_last4 varchar(4) not null,
                                   card_type varchar(32) not null,
                                   settled_amount decimal(19, 4) not null,
                                   interchange_fee decimal(19, 4) not null,
                                   processor_fee decimal(19, 4) not null,
                                   currency varchar(3) not null,
                                   settlement_date date not null,
                                   settlement_sign varchar(16) not null,

                                   constraint fk_settlement_record_import_batch
                                       foreign key (import_batch_id) references import_batch (id),

                                   constraint chk_settlement_sign
                                       check (settlement_sign in ('POSITIVE', 'NEGATIVE', 'ZERO')),

                                   constraint chk_settlement_currency
                                       check (currency = 'USD')
);

create table quarantined_record (
                                    id bigint generated always as identity primary key,
                                    import_batch_id bigint not null,
                                    source_type varchar(50) not null,
                                    source_row_number integer,
                                    source_record_key varchar(128),
                                    raw_payload clob not null,
                                    reason varchar(1000) not null,
                                    quarantined_at timestamp with time zone not null default current_timestamp,

                                    constraint fk_quarantined_record_import_batch
                                        foreign key (import_batch_id) references import_batch (id)
);

create table reconciliation_run (
                                    id bigint generated always as identity primary key,
                                    started_at timestamp with time zone not null default current_timestamp,
                                    completed_at timestamp with time zone,
                                    status varchar(32) not null,
                                    amount_tolerance decimal(19, 4) not null,
                                    normal_settlement_window_days integer not null,
                                    notes varchar(2000),

                                    constraint chk_reconciliation_run_status
                                        check (status in ('RUNNING', 'COMPLETED', 'FAILED'))
);

create table reconciliation_match (
                                      id bigint generated always as identity primary key,
                                      run_id bigint not null,
                                      internal_txn_id varchar(64),
                                      network_ref varchar(64),
                                      match_type varchar(64) not null,
                                      outcome varchar(64) not null,
                                      expected_settled_amount decimal(19, 4),
                                      expected_interchange_fee decimal(19, 4),
                                      expected_processor_fee decimal(19, 4),
                                      actual_settled_amount decimal(19, 4),
                                      reported_interchange_fee decimal(19, 4),
                                      reported_processor_fee decimal(19, 4),
                                      amount_delta decimal(19, 4),
                                      fee_delta decimal(19, 4),
                                      explanation varchar(2000),

                                      constraint fk_reconciliation_match_run
                                          foreign key (run_id) references reconciliation_run (id),

                                      constraint fk_reconciliation_match_internal
                                          foreign key (internal_txn_id) references internal_transaction (internal_txn_id),

                                      constraint fk_reconciliation_match_settlement
                                          foreign key (network_ref) references settlement_record (network_ref)
);

create table reconciliation_break (
                                      id bigint generated always as identity primary key,
                                      run_id bigint not null,
                                      category varchar(64) not null,
                                      merchant_id varchar(64),
                                      internal_txn_id varchar(64),
                                      network_ref varchar(64),
                                      amount decimal(19, 4) not null default 0,
                                      expected_amount decimal(19, 4),
                                      actual_amount decimal(19, 4),
                                      reason varchar(2000) not null,
                                      created_at timestamp with time zone not null default current_timestamp,

                                      constraint fk_reconciliation_break_run
                                          foreign key (run_id) references reconciliation_run (id),

                                      constraint fk_reconciliation_break_internal
                                          foreign key (internal_txn_id) references internal_transaction (internal_txn_id),

                                      constraint fk_reconciliation_break_settlement
                                          foreign key (network_ref) references settlement_record (network_ref)
);

create index idx_internal_merchant_ref_type
    on internal_transaction (merchant_ref, transaction_type);

create index idx_internal_fallback_match
    on internal_transaction (merchant_id, card_type, card_last4, currency, transaction_type);

create index idx_internal_merchant
    on internal_transaction (merchant_id);

create index idx_settlement_merchant_ref_sign
    on settlement_record (merchant_ref, settlement_sign);

create index idx_settlement_fallback_match
    on settlement_record (merchant_id, card_type, card_last4, currency, settlement_sign);

create index idx_settlement_merchant
    on settlement_record (merchant_id);

create index idx_quarantine_batch
    on quarantined_record (import_batch_id);

create index idx_match_run_outcome
    on reconciliation_match (run_id, outcome);

create index idx_break_run_category
    on reconciliation_break (run_id, category);

create index idx_break_run_merchant
    on reconciliation_break (run_id, merchant_id);