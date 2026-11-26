\set ON_ERROR_STOP on

-- Adds a repeatable 50-person recommendation neighborhood around Avery (user 5)
-- and the local profile that previously attempted to connect (user 10). Accepted edges use PostgreSQL as the source of
-- truth; the connection outbox copies them into Neo4j for 2/3-hop retrieval.

\connect userservice_db springuser
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM users WHERE id = 5 AND email = 'avery.demo@connectbro.example') THEN
    RAISE EXCEPTION 'Avery demo account (id 5) is missing; run the demo loader first.';
  END IF;
END $$;

BEGIN;
WITH bridge_profiles AS (
  SELECT n, 200000 + n AS id,
         (ARRAY['Maya Chen','Rahul Mehta','Sofia Laurent','James Okafor'])[n] AS name,
         (ARRAY['AI Research Mentor','Product Leadership Mentor','Climate Data Mentor','Startup Community Mentor'])[n] AS headline,
         (ARRAY['Python, Machine Learning, Responsible AI','Product Strategy, User Research, Data Storytelling','Climate Tech, Data Science, Analytics','Startup Strategy, Mentoring, Product Discovery'])[n] AS skills,
         (ARRAY['Responsible AI, Mentorship, Open Source','Early-stage teams, Product leadership, Mentorship','Climate tech, Data visualization, Collaboration','Early-stage teams, Mentorship, Community building'])[n] AS interests,
         (ARRAY['Mentor AI builders, Share practical lessons','Help product teams find useful problems','Build climate analytics, Mentor data teams','Help founders validate products'])[n] AS goals
  FROM generate_series(1,4) AS n
), candidate_profiles AS (
  SELECT n, 200009 + n AS id,
    (ARRAY['Ava','Noah','Priya','Arjun','Mia','Ethan','Sofia','Liam','Anika','Daniel'])[((n-1)%10)+1]
      || ' ' || (ARRAY['Chen','Patel','Garcia','Kim','Singh'])[((n-1)/10)+1] AS name,
    CASE WHEN n <= 13 THEN 'AI product builder'
         WHEN n <= 25 THEN 'Product researcher and strategist'
         WHEN n <= 38 THEN 'Climate and data specialist'
         ELSE 'Startup and open-source builder' END AS role,
    CASE WHEN n <= 13 THEN 'Product strategy, Python, Machine Learning, User Research'
         WHEN n <= 25 THEN 'Product Strategy, User Research, Data Storytelling, Product Discovery'
         WHEN n <= 38 THEN 'Data Science, Analytics, Climate Tech, Data Visualization'
         ELSE 'Product Discovery, Startup Strategy, Open Source, Prototyping' END AS skills,
    CASE WHEN n <= 13 THEN 'Responsible AI, Early-stage teams, AI products'
         WHEN n <= 25 THEN 'Early-stage teams, Product leadership, Accessibility'
         WHEN n <= 38 THEN 'Climate tech, Responsible AI, Data visualization'
         ELSE 'Open Source, Early-stage teams, Community building' END AS interests,
    CASE WHEN n <= 13 THEN 'Finding a mentor, Building useful AI products, Collaborating across disciplines'
         WHEN n <= 25 THEN 'Finding a mentor, Improving product discovery, Collaborating across disciplines'
         WHEN n <= 38 THEN 'Building useful AI products, Climate analytics, Finding a mentor'
         ELSE 'Finding a mentor, Building useful products, Collaborating across disciplines' END AS goals
  FROM generate_series(1,50) AS n
), profiles AS (
  SELECT id, name, headline, skills, interests, goals,
         'Synthetic profile for testing explainable profile and network recommendations.' AS about,
         (ARRAY['Bengaluru, India','Mumbai, India','London, UK','Toronto, Canada','Singapore'])[((n-1)%5)+1] AS location
  FROM bridge_profiles
  UNION ALL
  SELECT id, name, role, skills, interests, goals,
         'Synthetic profile with skills, interests, and goals for recommendation demos.' AS about,
         (ARRAY['Bengaluru, India','Mumbai, India','London, UK','Toronto, Canada','Singapore'])[((n-1)%5)+1] AS location
  FROM candidate_profiles
)
INSERT INTO users (id, name, email, password, headline, about, location, profile_picture, skills, interests, goals)
SELECT id, name, format('recommendation.demo.%s@connectbro.example',id),
       '$2a$10$YQfQ1./ytokmw8ixsP0oG.j8RdsDzCJlzugM4ZP1NuJAV52AUTLEu',
       headline, about, location, NULL, skills, interests, goals
FROM profiles
ON CONFLICT (id) DO UPDATE SET
  name=EXCLUDED.name, headline=EXCLUDED.headline, about=EXCLUDED.about,
  location=EXCLUDED.location, skills=EXCLUDED.skills, interests=EXCLUDED.interests, goals=EXCLUDED.goals;
SELECT setval(pg_get_serial_sequence('users','id'), GREATEST((SELECT max(id) FROM users),1), true);
COMMIT;
\echo 'Added 54 recommendation demo profiles.'

\connect checkpointdb springuser
BEGIN;
WITH edges AS (
  -- The demo account is one hop from four bridge profiles.
  SELECT 5::bigint AS a, 200000+n AS b FROM generate_series(1,4) n
  UNION ALL
  SELECT 10::bigint AS a, 200000+n AS b FROM generate_series(1,4) n
  UNION ALL
  -- 32 candidates are two hops away; some have two mutual bridge profiles.
  SELECT 200001+((n-1)/8), 200009+n FROM generate_series(1,32) n
  UNION ALL
  SELECT 200001+(((n-1)/8+1)%4), 200009+n
  FROM generate_series(1,32) n WHERE n%4=0
  UNION ALL
  -- The remaining 18 candidates are reachable at three hops.
  SELECT 200009+((n-33)%32+1), 200009+n FROM generate_series(33,50) n
  UNION ALL
  SELECT 200009+((n-33+11)%32+1), 200009+n FROM generate_series(33,50) n
), normalized AS (
  SELECT DISTINCT LEAST(a,b) AS low_user_id, GREATEST(a,b) AS high_user_id FROM edges
)
INSERT INTO connection_requests (low_user_id, high_user_id, requester_user_id, receiver_user_id, status, created_at, updated_at)
SELECT low_user_id, high_user_id, low_user_id, high_user_id, 'ACCEPTED', now(), now()
FROM normalized
ON CONFLICT (low_user_id, high_user_id) DO UPDATE SET
  status='ACCEPTED', updated_at=now();

WITH edges AS (
  SELECT 5::bigint AS a, 200000+n AS b FROM generate_series(1,4) n
  UNION ALL
  SELECT 10::bigint AS a, 200000+n AS b FROM generate_series(1,4) n
  UNION ALL
  SELECT 200001+((n-1)/8), 200009+n FROM generate_series(1,32) n
  UNION ALL
  SELECT 200001+(((n-1)/8+1)%4), 200009+n
  FROM generate_series(1,32) n WHERE n%4=0
  UNION ALL
  SELECT 200009+((n-33)%32+1), 200009+n FROM generate_series(33,50) n
  UNION ALL
  SELECT 200009+((n-33+11)%32+1), 200009+n FROM generate_series(33,50) n
), normalized AS (
  SELECT DISTINCT LEAST(a,b) AS user_a, GREATEST(a,b) AS user_b FROM edges
)
INSERT INTO connection_graph_outbox (action, user_a, user_b, attempts, created_at, processed_at)
SELECT 'UPSERT', user_a, user_b, 0, now(), NULL FROM normalized e
WHERE NOT EXISTS (
  SELECT 1 FROM connection_graph_outbox o
  WHERE o.action='UPSERT' AND o.user_a=e.user_a AND o.user_b=e.user_b
);
COMMIT;
\echo 'Added accepted 2-hop and 3-hop recommendation edges; graph sync will process the outbox.'
