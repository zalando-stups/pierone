ALTER TABLE zp_data.tags
  ADD COLUMN t_content_digest TEXT NULL;

CREATE INDEX tags_content_digest_idx ON zp_data.tags(t_content_digest);
