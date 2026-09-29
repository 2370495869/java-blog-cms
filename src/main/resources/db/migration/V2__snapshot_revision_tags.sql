CREATE TABLE article_revision_tags (
    revision_id BIGINT NOT NULL,
    tag_position INT NOT NULL,
    tag_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (revision_id, tag_position),
    CONSTRAINT fk_article_revision_tags_revision FOREIGN KEY (revision_id)
        REFERENCES article_revisions(id) ON DELETE CASCADE
);
