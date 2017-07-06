SET search_path TO zp_data;

CREATE TABLE scm_source_data_by_tag (
  ssdbt_team     TEXT NOT NULL,
  ssdbt_artifact TEXT NOT NULL,
  ssdbt_name     TEXT NOT NULL,

  ssdbt_url      TEXT NOT NULL,
  ssdbt_revision TEXT NOT NULL,
  ssdbt_author   TEXT NOT NULL,
  ssdbt_status   TEXT NOT NULL,
  ssdbt_created timestamp NOT NULL DEFAULT now(),

  PRIMARY KEY (ssdbt_team, ssdbt_artifact, ssdbt_name),
  FOREIGN KEY (ssdbt_team, ssdbt_artifact, ssdbt_name) REFERENCES tags (t_team, t_artifact, t_name)
);
