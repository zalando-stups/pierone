CREATE SCHEMA zp_data;
SET search_path TO zp_data;

-- base entity: images
CREATE TABLE images (

-- the official image ID
  i_id       TEXT NOT NULL,

-- the JSON metadata of the image
  i_metadata TEXT NOT NULL,

-- if the image was accepted (image binary and metadata are present)
  i_accepted BOOL NOT NULL,

-- the parent image of this image
  i_parent_id   TEXT,

  i_created timestamp NOT NULL DEFAULT now(),
  i_created_by TEXT,

  PRIMARY KEY (i_id)
);

-- base entity: tags
CREATE TABLE tags (
--the team ID
  t_team     TEXT NOT NULL,

-- the artifact name
  t_artifact TEXT NOT NULL,

-- the artifact tag
  t_name     TEXT NOT NULL,

-- the referenced image
  t_image_id    TEXT NOT NULL,

  t_created timestamp NOT NULL DEFAULT now(),
  t_created_by TEXT,

  PRIMARY KEY (t_team, t_artifact, t_name),
  FOREIGN KEY (t_image_id) REFERENCES images (i_id)
);