-- name: read-tags
SELECT t_name AS name,
       t_image_id AS image
  FROM tags
 WHERE t_team = :team
   AND t_artifact = :artifact;

-- name: create-tag!
INSERT INTO tags
       (t_team, t_artifact, t_name, t_image_id, t_created_by)
VALUES (:team, :artifact, :name, :image, :user);

-- name: update-tag!
UPDATE tags
   SET t_image_id = :image
 WHERE t_team = :team
   AND t_artifact = :artifact
   AND t_name = :name;

-- name: create-image!
INSERT INTO images
       (i_id, i_metadata, i_accepted, i_parent_id, i_created_by)
VALUES (:image, :metadata, FALSE, :parent, :user);

-- name: delete-unaccepted-image!
DELETE FROM images
WHERE i_id = :image
      AND i_accepted = FALSE;

-- name: accept-image!
UPDATE images
   SET i_accepted = TRUE
 WHERE i_id = :image;

-- name: create-scm-source-data!
INSERT INTO scm_source_data
       (ssd_image_id, ssd_url, ssd_revision, ssd_author, ssd_status)
VALUES (:image, :url, :revision, :author, :status);

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

-- name: search-repos
  SELECT t_team || '/' || t_artifact AS name
    FROM tags
   WHERE t_team = :q
      OR t_artifact = :q
GROUP BY name
ORDER BY name
