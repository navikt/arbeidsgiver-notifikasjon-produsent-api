
create table wait_queue
(
    id bigserial primary key,
    varsel_id uuid not null,
    -- norsk veggklokketidspunkt for når jobben skal vekkes til live igjen.
    resume_job_at timestamp without time zone
);

create index idx_wait_queue_resume_job_at on wait_queue(resume_job_at);

create unique index idx_job_queue_varsel_id_unique on job_queue(varsel_id);

