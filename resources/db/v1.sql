-- name: read-tags
SELECT t_name AS name,
       t_image_id AS image,
       t_created AS created
  FROM tags
 WHERE t_team = :team
   AND t_artifact = :artifact;

-- name: create-tag!
INSERT INTO tags
       (t_team, t_artifact, t_name, t_image_id, t_created_by)
VALUES (:team, :artifact, :name, :image, :user);

-- name: update-tag!
UPDATE tags
   SET t_image_id = :image,
       -- updating is like overwriting (delete+create)
       -- i.e. update created timestamp and user
       t_created_by = :user,
       t_created = now()
 WHERE t_team = :team
   AND t_artifact = :artifact
   AND t_name = :name
   AND t_image_id != :image;

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
   SET i_accepted = TRUE,
       i_size = :size
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

-- name: get-image-ancestry
  WITH RECURSIVE parents(i_id)
    AS (VALUES (:image)
         UNION
        SELECT img.i_parent_id
          FROM images img, 
               parents p
         WHERE img.i_id = p.i_id
           AND img.i_accepted = TRUE)
SELECT i_id AS id
  FROM parents
 WHERE i_id IS NOT NULL
