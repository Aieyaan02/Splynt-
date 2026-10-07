ALTER TABLE clover_oauth_credentials ADD COLUMN inventory_issues_json TEXT;
ALTER TABLE clover_oauth_credentials ADD COLUMN inventory_issue_count INTEGER NOT NULL DEFAULT 0;
