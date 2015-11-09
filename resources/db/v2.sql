-- name: create-image-blob!
INSERT INTO images
       (i_id, i_metadata, i_accepted, i_parent_id, i_size, i_created_by)
VALUES (:image, '', FALSE, NULL, :size, :user);

-- name: image-blob-exists
SELECT 1
  FROM images
 WHERE i_id = :image;

-- name: get-blob-size
SELECT i_size AS size
  FROM images
 WHERE i_id = :image;

-- name: accept-image-blob!
UPDATE images
   SET i_accepted = TRUE
 WHERE i_id = :image;

-- name: create-manifest!
INSERT INTO tags
       (t_team, t_artifact, t_name, t_manifest, t_image_id, t_fs_layers, t_created_by)
VALUES (:team, :artifact, :name, :manifest, :image, ARRAY[ :fs_layers ], :user);

-- name: update-manifest!
UPDATE tags
   SET t_image_id = :image,
       t_manifest = :manifest,
       t_fs_layers = ARRAY[ :fs_layers ],
       -- updating is like overwriting (delete+create)
       -- i.e. update created timestamp and user
       t_created_by = :user,
       t_created = now()
 WHERE t_team = :team
   AND t_artifact = :artifact
   AND t_name = :name
   AND t_manifest != :manifest;

-- name: get-manifest
SELECT t_manifest AS manifest
  FROM tags
 WHERE t_team = :team
   AND t_artifact = :artifact
   AND t_name = :name;

-- name: list-tag-names
SELECT t_name AS name
  FROM tags
 WHERE t_team = :team
   AND t_artifact = :artifact;

-- name: list-repositories
SELECT t_team || '/' || t_artifact AS name
  FROM tags;
