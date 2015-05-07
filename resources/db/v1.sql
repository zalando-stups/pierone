-- name: read-tags
SELECT t_name AS name,
       t_image_id AS image
  FROM tags
 WHERE t_team = :team
   AND t_artifact = :artifact;

-- name: create-tag!
INSERT INTO tags
       (t_team, t_artifact, t_name, t_image_id)
VALUES (:team, :artifact, :name, :image);

-- name: update-tag!
UPDATE tags
   SET t_image_id = :image
 WHERE t_team = :team
   AND t_artifact = :artifact
   AND t_name = :name;

-- name: create-image!
INSERT INTO images
       (i_id, i_metadata, i_accepted, i_parent_id)
VALUES (:image, :metadata, FALSE, :parent);

-- name: delete-unaccepted-image!
DELETE FROM images
WHERE i_id = :image
      AND i_accepted = FALSE;

-- name: accept-image!
UPDATE images
   SET i_accepted = TRUE
 WHERE i_id = :image;

-- name: get-image-metadata
SELECT i_metadata AS metadata
  FROM images
 WHERE i_id = :image
   AND i_accepted = TRUE;

-- name: get-image-parent
SELECT i_parent_id AS parent
  FROM images
 WHERE i_id = :image
   AND i_accepted = TRUE;