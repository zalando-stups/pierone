ALTER TABLE zp_data.tags
  ADD COLUMN t_severity_fix_available TEXT NULL;

ALTER TABLE zp_data.tags
  ADD COLUMN t_severity_no_fix_available TEXT NULL;

ALTER TABLE zp_data.tags
  ADD COLUMN t_clair_id TEXT NULL;
