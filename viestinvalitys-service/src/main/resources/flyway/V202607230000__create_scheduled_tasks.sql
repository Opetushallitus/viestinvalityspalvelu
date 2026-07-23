-- db-scheduler (kagkarlsson) task store. Schema must match the resolved db-scheduler version
-- (16.12.0). Copied from the tiedotuspalvelu migration.
CREATE TABLE scheduled_tasks (
    task_name            text                     NOT NULL,
    task_instance        text                     NOT NULL,
    task_data            bytea,
    execution_time       timestamp with time zone NOT NULL,
    picked               boolean                  NOT NULL,
    picked_by            text,
    last_success         timestamp with time zone,
    last_failure         timestamp with time zone,
    consecutive_failures integer,
    priority             integer,
    last_heartbeat       timestamp with time zone,
    version              bigint                   NOT NULL,
    PRIMARY KEY (task_name, task_instance)
);
CREATE INDEX idx_scheduled_tasks_execution_time ON scheduled_tasks (execution_time);
