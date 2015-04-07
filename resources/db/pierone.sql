-- name: list-teams
SELECT team
  FROM tag
 GROUP BY team;

-- name: list-artifacts
SELECT artifact
  FROM tag
 WHERE team = :team
 GROUP BY artifact;

-- name: list-tags
SELECT name
  FROM tag
 WHERE team = :team
   AND artifact = :artifact;
