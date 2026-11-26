\set ON_ERROR_STOP on
\set user_count 1000

-- All generated accounts use the local-only password: ConnectBroDemo2026!
-- Stable IDs and email/content markers make this seed safe to rerun.
\connect userservice_db springuser
BEGIN;
INSERT INTO users (id, name, email, password, headline, about, location, profile_picture, skills, interests, goals)
SELECT 100000 + n,
       (ARRAY['Avery','Jordan','Taylor','Riley','Morgan','Casey','Quinn','Reese','Cameron','Alex','Sam','Jamie','Priya','Arjun','Maya','Noah','Aisha','Ethan','Sofia','Liam','Anika','Daniel','Nia','Lucas','Zara','Omar','Emma','Mateo','Leila','Isaac','Chloe','Ravi','Amara','Theo','Elena','Kabir','Grace','Julian','Isha','Caleb'])[((n - 1) % 40) + 1]
           || ' ' ||
       (ARRAY['Chen','Patel','Garcia','Kim','Singh','Martin','Brown','Wilson','Shah','Lopez','Davis','Nguyen','Mehta','Laurent','Okafor','Hassan','Wright','Park','Silva','Reed','Cooper','Bennett','Brooks','Turner','Flores'])[((n - 1) / 40) + 1],
       format('demo.user.%s@example.test', lpad(n::text, 5, '0')),
       '$2a$10$YQfQ1./ytokmw8ixsP0oG.j8RdsDzCJlzugM4ZP1NuJAV52AUTLEu',
       (ARRAY['Software Engineer','Product Designer','Data Scientist','Founder','Product Manager','ML Engineer','Community Builder','Security Engineer'])[((n - 1) % 8) + 1],
       format('Synthetic ConnectBro member %s. Interested in building useful products and sharing practical knowledge with the community.', lpad(n::text, 5, '0')),
       (ARRAY['Bengaluru, India','Mumbai, India','Delhi, India','Hyderabad, India','Pune, India','Chennai, India','Singapore','London, UK','Toronto, Canada','San Francisco, USA'])[((n * 3 - 1) % 10) + 1],
       NULL,
       (ARRAY['Java, Spring Boot, PostgreSQL','Python, Machine Learning, FastAPI','React, TypeScript, UX Research','Kubernetes, AWS, Terraform','Product Strategy, Analytics, SQL','LLMs, Python, Vector Search','Go, Distributed Systems, Kafka','Security, OAuth, Threat Modeling'])[((n - 1) % 8) + 1]
           || ', ' ||
       (ARRAY['System Design','Open Source','Data Visualization','Accessibility','Cloud Architecture','Experimentation','Graph Databases','API Design'])[((n * 5 - 1) % 8) + 1],
       (ARRAY['AI products, open source','Climate tech, startups','Developer tools, design','Education, mentorship','Fintech, data','Health technology, AI','Community building, startups','Privacy, security'])[((n * 3 - 1) % 8) + 1],
       (ARRAY['Find a mentor, grow into leadership','Build an AI product, meet collaborators','Change careers, strengthen my portfolio','Launch a startup, find early teammates','Learn system design, contribute to open source','Grow my network, share practical ideas','Find product feedback, validate an idea','Develop expertise, mentor other builders'])[((n * 7 - 1) % 8) + 1]
FROM generate_series(1, :user_count) AS n
ON CONFLICT DO NOTHING;
UPDATE users u
SET name = (ARRAY['Avery','Jordan','Taylor','Riley','Morgan','Casey','Quinn','Reese','Cameron','Alex','Sam','Jamie','Priya','Arjun','Maya','Noah','Aisha','Ethan','Sofia','Liam','Anika','Daniel','Nia','Lucas','Zara','Omar','Emma','Mateo','Leila','Isaac','Chloe','Ravi','Amara','Theo','Elena','Kabir','Grace','Julian','Isha','Caleb'])[((n - 1) % 40) + 1]
    || ' ' ||
       (ARRAY['Chen','Patel','Garcia','Kim','Singh','Martin','Brown','Wilson','Shah','Lopez','Davis','Nguyen','Mehta','Laurent','Okafor','Hassan','Wright','Park','Silva','Reed','Cooper','Bennett','Brooks','Turner','Flores'])[((n - 1) / 40) + 1]
FROM generate_series(1, :user_count) AS n
WHERE u.id = 100000 + n;
SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST(COALESCE((SELECT max(id) FROM users), 1), 1), true);
COMMIT;
\echo 'Seeded synthetic profiles in userservice_db.'

\connect postservice_db springuser
BEGIN;
WITH seed_posts AS (
    SELECT 100000 + n AS user_id, post_no,
           format('[seed-user-%s-post-%s] I am exploring %s and sharing what I learn with the ConnectBro community. What has worked for you?',
                  lpad(n::text, 5, '0'), post_no,
                  (ARRAY['AI tools','climate products','developer experience','data platforms','community-led growth','accessible design','secure systems','product discovery'])[((n + post_no - 2) % 8) + 1]) AS content
    FROM generate_series(1, :user_count) AS n
    CROSS JOIN generate_series(1, 3) AS post_no
)
INSERT INTO post (user_id, content, created_at)
SELECT user_id, content, now() - ((post_no * 3 + user_id % 21) * interval '1 day')
FROM seed_posts s
WHERE NOT EXISTS (SELECT 1 FROM post p WHERE p.content = s.content);

WITH seed_posts AS (
    SELECT id, user_id
    FROM post
    WHERE content LIKE '[seed-user-%'
), generated AS (
    SELECT p.id AS post_id,
           100000 + ((((p.user_id - 100000 + offset_no - 1) % :user_count) + :user_count) % :user_count) + 1 AS user_id,
           offset_no
    FROM seed_posts p
    CROSS JOIN (VALUES (1), (13), (47)) AS offsets(offset_no)
)
INSERT INTO post_likes (user_id, post_id, created_at)
SELECT g.user_id, g.post_id, now() - (g.offset_no * interval '1 hour')
FROM generated g
WHERE NOT EXISTS (SELECT 1 FROM post_likes l WHERE l.user_id = g.user_id AND l.post_id = g.post_id);

WITH generated AS (
    SELECT p.id AS post_id, 100000 + (((p.user_id - 100000 + 29) % :user_count) + 1) AS user_id,
           format('[seed-comment-%s] Nice progress on this. I would love to compare notes.', p.id) AS content
    FROM post p
    WHERE p.content LIKE '[seed-user-%' AND p.content LIKE '%-post-3]%'
)
INSERT INTO post_comments (post_id, user_id, content, created_at)
SELECT g.post_id, g.user_id, g.content, now() - interval '2 hours'
FROM generated g
WHERE NOT EXISTS (SELECT 1 FROM post_comments c WHERE c.content = g.content);
COMMIT;
\echo 'Seeded synthetic posts, likes, and comments in postservice_db.'

\connect checkpointdb springuser
BEGIN;
WITH user_ids AS (
    SELECT n AS seq, 100000 + n AS user_id FROM generate_series(1, :user_count) AS n
), offsets(step) AS (VALUES (1), (7), (31)), edges AS (
    SELECT DISTINCT LEAST(a.user_id, b.user_id) AS low_user_id,
           GREATEST(a.user_id, b.user_id) AS high_user_id
    FROM user_ids a
    CROSS JOIN offsets o
    JOIN user_ids b ON b.seq = ((a.seq + o.step - 1) % :user_count) + 1
)
INSERT INTO connection_requests (low_user_id, high_user_id, requester_user_id, receiver_user_id, status, created_at, updated_at)
SELECT low_user_id, high_user_id, low_user_id, high_user_id, 'ACCEPTED', now(), now()
FROM edges
ON CONFLICT (low_user_id, high_user_id) DO NOTHING;

WITH user_ids AS (
    SELECT n AS seq, 100000 + n AS user_id FROM generate_series(1, :user_count) AS n
), offsets(step) AS (VALUES (1), (7), (31)), edges AS (
    SELECT DISTINCT LEAST(a.user_id, b.user_id) AS low_user_id,
           GREATEST(a.user_id, b.user_id) AS high_user_id
    FROM user_ids a
    CROSS JOIN offsets o
    JOIN user_ids b ON b.seq = ((a.seq + o.step - 1) % :user_count) + 1
)
INSERT INTO connection_graph_outbox (action, user_a, user_b, attempts, created_at, processed_at)
SELECT 'UPSERT', e.low_user_id, e.high_user_id, 0, now(), NULL
FROM edges e
WHERE NOT EXISTS (
    SELECT 1 FROM connection_graph_outbox o
    WHERE o.action = 'UPSERT' AND o.user_a = e.low_user_id AND o.user_b = e.high_user_id
);
COMMIT;
\echo 'Seeded accepted connections and graph-sync events in checkpointdb.'

\connect "notification-service_db" springuser
BEGIN;
INSERT INTO notification (user_id, message, type, reference_id, is_read, created_at)
SELECT 100000 + n,
       format('Your synthetic network is growing: %s builders share skills and interests with you.', (n % 19) + 4),
       'connection', 100000 + ((n % :user_count) + 1), false, now() - ((n % 14) * interval '1 day')
FROM generate_series(1, :user_count) AS n
WHERE n % 2 = 0
  AND NOT EXISTS (
      SELECT 1 FROM notification x
      WHERE x.user_id = 100000 + n AND x.message LIKE 'Your synthetic network is growing:%'
  );
COMMIT;
\echo 'Seeded synthetic notifications.'
