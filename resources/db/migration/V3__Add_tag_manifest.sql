ALTER TABLE zp_data.tags
  ADD COLUMN t_manifest TEXT NULL;

ALTER TABLE zp_data.tags
  ADD COLUMN t_fs_layers TEXT[] NULL;
