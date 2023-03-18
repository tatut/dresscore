CREATE TYPE program_part AS (
  id INT,
  name TEXT
);

CREATE TABLE program (
  id SERIAL PRIMARY KEY,
  name TEXT,
  parts program_part[]
);

CREATE TYPE score AS (
  part_id INT,
  score NUMERIC
);

CREATE TABLE person (
  id SERIAL PRIMARY KEY,
  name TEXT,
  is_judge BOOL, -- will appear in judge search
  is_rider BOOL  -- will appear in rider search
);

CREATE TYPE horse_gender AS ENUM ('stallion', 'mare', 'gelding');

CREATE TABLE horse (
  id SERIAL PRIMARY KEY,
  name text,
  birthdate DATE,
  gender horse_gender
);

CREATE TABLE scoring (
  id SERIAL PRIMARY KEY,
  program_id BIGINT REFERENCES program (id),
  date DATE,
  scores score[],
  judge_id BIGINT REFERENCES person (id),
  rider_id BIGINT REFERENCES person (id),
  horse_id BIGINT REFERENCES horse (id)
);

-- Program names are unique
CREATE UNIQUE INDEX program_name_idx ON program (name);

-- One judge can give one scoring per day/program/rider/horse combo
CREATE UNIQUE INDEX scoring_idx ON scoring (program_id, date, rider_id, horse_id, judge_id);

CREATE VIEW percentages AS (
WITH prg  AS (
  SELECT id, 10.0 * array_length(parts,1) as max_points
    FROM program
) SELECT s.program_id, s.date, s.rider_id, s.horse_id, s.judge_id,
         SUM(ss.score) as points,
         (SELECT max_points FROM prg WHERE prg.id = s.program_id) as max_points,
         (SUM(ss.score) * 100.0 / (SELECT max_points FROM prg WHERE prg.id = s.program_id)) as percentage
    FROM scoring s
    JOIN LATERAL unnest(s.scores) ss on true
GROUP BY s.program_id, s.date, s.rider_id, s.horse_id, s.judge_id
);
