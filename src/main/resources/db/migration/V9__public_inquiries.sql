CREATE TABLE contact_inquiries (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    business VARCHAR(150),
    message VARCHAR(3000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_contact_created ON contact_inquiries(created_at);
CREATE INDEX ix_contact_email_created ON contact_inquiries(email, created_at);
