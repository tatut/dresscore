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
  part INT,
  score INT
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
  lower_percentage NUMERIC,
  upper_percentage NUMERIC,
  judge_id BIGINT REFERENCES person (id),
  rider_id BIGINT REFERENCES person (id),
  horse_id BIGINT REFERENCES horse (id)
);

-- Program names are unique
CREATE UNIQUE INDEX program_name_idx ON program (name);

-- One judge can give one scoring per day/program/rider/horse combo
CREATE UNIQUE INDEX scoring_idx ON scoring (program_id, date, rider_id, horse_id, judge_id);
