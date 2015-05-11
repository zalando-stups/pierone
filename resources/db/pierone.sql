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
       t_created_by AS created_by
  FROM tags
 WHERE t_team = :team
   AND t_artifact = :artifact;

-- name: get-scm-source
SELECT ssd_url AS url,
       ssd_revision AS revision,
       ssd_author AS author,
       ssd_status AS status,
       ssd_created AS created
  FROM tags
  JOIN scm_source_data
    ON ssd_image_id = t_image_id
 WHERE t_team = :team
   AND t_artifact = :artifact
   AND t_name = :tag;

