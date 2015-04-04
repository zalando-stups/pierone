-- base entity: images
CREATE TABLE image (

-- the official image ID
  id       TEXT NOT NULL,

-- the JSON metadata of the image
  metadata TEXT NOT NULL,

-- if the image was accepted (image binary and metadata are present)
  accepted BOOL NOT NULL,

-- the parent image of this image
  parent   TEXT,

  PRIMARY KEY (id)
);

-- base entity: tags
CREATE TABLE tag (
--the team ID
  team     TEXT NOT NULL,

-- the artifact name
  artifact TEXT NOT NULL,

-- the artifact tag
  name     TEXT NOT NULL,

-- the referenced image
  image    TEXT NOT NULL,

  PRIMARY KEY (team, artifact, name),
  FOREIGN KEY (image) REFERENCES image (id)
);