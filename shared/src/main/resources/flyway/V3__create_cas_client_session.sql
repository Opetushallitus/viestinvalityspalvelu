CREATE UNLOGGED TABLE raportointi_cas_client_session (
    mapped_ticket_id VARCHAR PRIMARY KEY,
    raportointi_session_id CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT raportointi_cas_client_session_fk FOREIGN KEY (raportointi_session_id) REFERENCES raportointi_session(session_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX raportointi_cas_client_session_ix1 ON raportointi_cas_client_session (mapped_ticket_id);

CREATE UNLOGGED TABLE lahetys_cas_client_session (
    mapped_ticket_id VARCHAR PRIMARY KEY,
    lahetys_session_id CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT lahetys_cas_client_session_fk FOREIGN KEY (lahetys_session_id) REFERENCES lahetys_session(session_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX lahetys_cas_client_session_ix1 ON lahetys_cas_client_session (mapped_ticket_id);
