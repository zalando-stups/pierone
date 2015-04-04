-- name: read-tags
SELECT name,
       image
  FROM tag
 WHERE team = :team
   AND artifact = :artifact;

-- name: create-tag!
INSERT INTO tag
       (team, artifact, name, image)
VALUES (:team, :artifact, :name, :image);

-- name: update-tag!
UPDATE tag
   SET image = :image
 WHERE team = :team
   AND artifact = :artifact
   AND name = :name;

-- name: create-image!
INSERT INTO image
       (id, metadata, accepted, parent)
VALUES (:image, :metadata, FALSE, :parent);

-- name: delete-unaccepted-image!
DELETE FROM image
WHERE id = :image
      AND accepted = FALSE;

-- name: accept-image!
UPDATE image
   SET accepted = TRUE
 WHERE id = :image;

-- name: get-image-metadata
SELECT metadata
  FROM image
 WHERE id = :image
   AND accepted = TRUE;

-- name: get-image-parent
SELECT parent
  FROM image
 WHERE id = :image
   AND accepted = TRUE;