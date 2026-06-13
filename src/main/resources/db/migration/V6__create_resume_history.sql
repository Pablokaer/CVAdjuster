CREATE TABLE resume_history (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    job_description_snippet VARCHAR(200),

    tailored_text TEXT,

    format VARCHAR(10),

    pdf_filename VARCHAR(255),

    docx_filename VARCHAR(255),

    CONSTRAINT fk_resume_history_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);