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
       t_image_id AS image,
       t_clair_id AS clair_id,
       t_severity_fix_available AS severity_fix_available,
       t_severity_no_fix_available AS severity_no_fix_available
  FROM tags
 WHERE t_team = :team
   AND t_artifact = :artifact
ORDER BY t_created;

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
-- latest approach: use separate table to store SCM-Source info that came in X-SCM-Source header
-- it has higher precedence over layer-specific data
SELECT ssdbt_url AS url,
       ssdbt_revision AS revision,
       ssdbt_author AS author,
       ssdbt_status AS status,
       ssdbt_created AS created
  FROM scm_source_data_by_tag
 WHERE ssdbt_team = :team
   AND ssdbt_artifact = :artifact
   AND ssdbt_name = :tag
UNION
-- v1: images pushed through /v1 API (DEPRECATED)
SELECT ssd_url AS url,
       ssd_revision AS revision,
       ssd_author AS author,
       ssd_status AS status,
       ssd_created AS created
  FROM parents
  JOIN scm_source_data ON ssd_image_id = i_id
UNION
-- v2: manifest with FS layer references
SELECT ssd_url AS url,
       ssd_revision AS revision,
       ssd_author AS author,
       ssd_status AS status,
       ssd_created AS created
  FROM tags
  JOIN scm_source_data ON ssd_image_id = ANY(t_fs_layers)
 WHERE t_team = :team
   AND t_artifact = :artifact
   AND t_name = :tag
ORDER BY created DESC
LIMIT 1;

-- name: get-total-storage
SELECT SUM(i_size) AS total
  FROM images
 WHERE i_accepted IS TRUE;

-- name: update-tag-severity!
UPDATE tags
   SET t_severity_fix_available = :severity_fix_available,
       t_severity_no_fix_available = :severity_no_fix_available
 WHERE t_clair_id = :clair_id;

-- name: insert-scm-source-data-by-tag!
INSERT INTO scm_source_data_by_tag
  (ssdbt_team, ssdbt_artifact, ssdbt_name, ssdbt_url, ssdbt_revision, ssdbt_author, ssdbt_status)
VALUES(:team, :artifact, :name, :url, :revision, :author, :status);
