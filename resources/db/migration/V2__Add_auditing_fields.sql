-- add columns
ALTER TABLE zp_data.tags
 ADD COLUMN t_last_modified TIMESTAMP DEFAULT NOW(),
 ADD COLUMN t_last_modified_by TEXT;

-- fill with data from created* fields
UPDATE zp_data.tags
   SET t_last_modified = COALESCE(t_last_modified, t_created),
       t_last_modified_by = COALESCE(t_last_modified_by, t_created_by);

-- set not null
 ALTER TABLE zp_data.tags
ALTER COLUMN t_last_modified SET NOT NULL,
ALTER COLUMN t_last_modified_by SET NOT NULL;
