-- name: list-teams
SELECT t_team AS team
  FROM tags
 GROUP BY t_team;

-- name: list-artifacts
SELECT t_artifact
  FROM tags
 WHERE t_team = :team
 GROUP BY t_artifact;

-- name: list-tags
SELECT t_name
  FROM tags
 WHERE t_team = :team
   AND t_artifact = :artifact;
