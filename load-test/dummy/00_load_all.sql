-- 전체 적재 진입점. 사용법은 README.md 참고.
--   풀 규모:   psql -f 00_load_all.sql
--   축소 규모: psql -v SCALE=0.01 -f 00_load_all.sql
\ir _helpers.sql

\echo '== 01 cleanup =='
\ir 01_cleanup.sql
\echo '== 10 users + social_accounts =='
\ir 10_users.sql
\echo '== 20 contents + tags =='
\ir 20_contents.sql
\echo '== 30 reviews (+ rating 집계) =='
\ir 30_reviews.sql
\echo '== 40 follows + playlists + subscriptions (+ subscriber_count 집계) =='
\ir 40_social.sql
\echo '== 50 conversations + members + direct_messages =='
\ir 50_dm.sql
\echo '== 60 notifications + watching_sessions =='
\ir 60_misc.sql
\echo '== ANALYZE =='
ANALYZE;
\echo '== 완료. 90_verify.sql 로 검증하세요 =='
