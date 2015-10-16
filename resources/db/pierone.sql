-- name: list-teams
SELECT t_team AS team
  FROM tags
 GROUP BY t_team;

-- name: list-artifacts
SELECT t_artifact AS artifact
  FROM tags
 WHERE t_team = :team
 GROUP BY t_artifact;

-- name: list-tags
SELECT t_name AS name,
       t_created AS created,
       t_created_by AS created_by,
       t_image_id AS image
  FROM tags
 WHERE t_team = :team
   AND t_artifact = :artifact;

-- name: list-images
SELECT i_id AS id,
       i_size AS size
  FROM images;

-- name: get-images
SELECT i_id AS id
  FROM images
 WHERE i_id LIKE (:image || '%');

-- name: list-tags-for-image
SELECT t_name AS name,
       t_team AS team,
       t_artifact AS artifact
  FROM tags
 WHERE t_image_id LIKE (:image || '%');

-- name: get-scm-source
WITH RECURSIVE parents(i_id)
    AS (SELECT t_image_id
          FROM tags
         WHERE t_team = :team
           AND t_artifact = :artifact
           AND t_name = :tag
         UNION
        SELECT img.i_parent_id
          FROM images img,
               parents p
         WHERE img.i_id = p.i_id
           AND img.i_accepted = TRUE)
SELECT ssd_url AS url,
       ssd_revision AS revision,
       ssd_author AS author,
       ssd_status AS status,
       ssd_created AS created
  FROM parents
  JOIN scm_source_data ON ssd_image_id = i_id;

-- name: get-total-storage
SELECT SUM(i_size) AS total
  FROM images
 WHERE i_accepted IS TRUE;
