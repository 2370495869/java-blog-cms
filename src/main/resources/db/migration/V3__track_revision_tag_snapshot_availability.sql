ALTER TABLE article_revisions
    ADD COLUMN tags_snapshot_available BOOLEAN NOT NULL DEFAULT FALSE;
