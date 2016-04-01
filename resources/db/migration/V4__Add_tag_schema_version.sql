SET search_path TO zp_data;

ALTER TABLE tags DROP CONSTRAINT tags_pkey;

ALTER TABLE tags
 ADD COLUMN t_schema_version SMALLINT DEFAULT 1;

ALTER TABLE tags ADD PRIMARY KEY (t_team, t_artifact, t_name, t_schema_version);
