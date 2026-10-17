-- 이미 입력된 시연 회차의 시각만 자연스러운 한국 시각으로 옮긴다.
-- 자동 실행하지 않는다. 1단계로 변경 전후를 확인한 뒤 2단계를 실행한다.
--
-- 대상: 제목이 '[DEMO] '로 시작하고 본문이 '[42clubs-demo-v1]'로 시작하며
--       주최자가 예약된 음수 intra_id(<= -420000)인 시연 소모임의 회차.
-- 보존: id, 참가 관계, 출결, 실제 사용자 자료는 건드리지 않는다.
--       TRUNCATE·전체 삭제·마이그레이션 변경 없음.

-- === 1단계: 변경 전후 비교 (읽기만 한다) ===
WITH target AS (
  SELECT * FROM (VALUES
    ('[DEMO] 금요일 커피 한 잔 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 5 + 6) % 7) + 1) * interval '1 day' + interval '15 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 금요일 커피 한 잔 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((5 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '15 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 한 달 한 권 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 3 + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 한 달 한 권 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((3 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 같이 푸는 알고리즘 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 2 + 6) % 7) + 1) * interval '1 day' + interval '20 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 같이 푸는 알고리즘 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((2 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '20 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 저녁 산책 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 4 + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 저녁 산책 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((4 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 주말 가벼운 등산 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 6 + 6) % 7) + 1) * interval '1 day' + interval '9 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 주말 가벼운 등산 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((6 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '9 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 그림 한 장 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 1 + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 그림 한 장 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((1 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 짧은 글쓰기 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 7 + 6) % 7) + 1) * interval '1 day' + interval '16 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 짧은 글쓰기 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((7 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '16 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 근교 여행 계획 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 7 + 6) % 7) + 1) * interval '1 day' + interval '14 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 근교 여행 계획 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((7 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '14 hours') AT TIME ZONE 'Asia/Seoul'))
  ) AS t(session_title, new_starts_at)
)
SELECT g.title AS gathering,
       s.title AS session,
       to_char(s.starts_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD Dy HH24:MI') AS before_kst,
       to_char(t.new_starts_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD Dy HH24:MI') AS after_kst
  FROM gathering_sessions s
  JOIN gatherings g ON g.id = s.gathering_id
  JOIN target t ON t.session_title = s.title
 WHERE g.title LIKE '[DEMO] %'
   AND g.content LIKE '[42clubs-demo-v1]%'
   AND EXISTS (SELECT 1 FROM users u WHERE u.id = g.organizer_id AND u.intra_id <= -420000)
 ORDER BY g.title, s.title;

-- === 2단계: 실제 변경 (한 트랜잭션) ===
BEGIN;

WITH target AS (
  SELECT * FROM (VALUES
    ('[DEMO] 금요일 커피 한 잔 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 5 + 6) % 7) + 1) * interval '1 day' + interval '15 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 금요일 커피 한 잔 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((5 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '15 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 한 달 한 권 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 3 + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 한 달 한 권 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((3 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 같이 푸는 알고리즘 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 2 + 6) % 7) + 1) * interval '1 day' + interval '20 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 같이 푸는 알고리즘 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((2 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '20 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 저녁 산책 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 4 + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 저녁 산책 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((4 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 주말 가벼운 등산 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 6 + 6) % 7) + 1) * interval '1 day' + interval '9 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 주말 가벼운 등산 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((6 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '9 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 그림 한 장 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 1 + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 그림 한 장 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((1 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '19 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 짧은 글쓰기 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 7 + 6) % 7) + 1) * interval '1 day' + interval '16 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 짧은 글쓰기 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((7 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '16 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 근교 여행 계획 첫 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') - (((EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int - 7 + 6) % 7) + 1) * interval '1 day' + interval '14 hours') AT TIME ZONE 'Asia/Seoul')),
    ('[DEMO] 근교 여행 계획 다음 만남', ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + (((7 - EXTRACT(ISODOW FROM now() AT TIME ZONE 'Asia/Seoul')::int + 6) % 7) + 1) * interval '1 day' + interval '14 hours') AT TIME ZONE 'Asia/Seoul'))
  ) AS t(session_title, new_starts_at)
)
UPDATE gathering_sessions s
   SET starts_at = t.new_starts_at,
       ends_at = t.new_starts_at + interval '2 hours',
       recruitment_closes_at = t.new_starts_at - interval '2 hours',
       recruitment_opens_at = LEAST(s.recruitment_opens_at,
                                    t.new_starts_at - interval '2 hours'),
       updated_at = now()
  FROM target t, gatherings g
 WHERE g.id = s.gathering_id
   AND t.session_title = s.title
   AND g.title LIKE '[DEMO] %'
   AND g.content LIKE '[42clubs-demo-v1]%'
   AND EXISTS (SELECT 1 FROM users u WHERE u.id = g.organizer_id AND u.intra_id <= -420000);

-- 동아리 활동은 날짜는 두고 시각만 저녁 7시로 맞춘다
UPDATE activities a
   SET starts_at = ((date_trunc('day', a.starts_at AT TIME ZONE 'Asia/Seoul')
                     + interval '19 hours') AT TIME ZONE 'Asia/Seoul'),
       recruitment_closes_at = ((date_trunc('day', a.starts_at AT TIME ZONE 'Asia/Seoul')
                     + interval '19 hours') AT TIME ZONE 'Asia/Seoul') - interval '2 hours',
       updated_at = now()
  FROM clubs c
 WHERE c.id = a.club_id
   AND a.title LIKE '[DEMO] %'
   AND c.name LIKE '[DEMO] %';

-- 제약 확인: 어긋나면 예외를 던져 되돌린다
DO $$
DECLARE bad int;
BEGIN
  SELECT count(*) INTO bad FROM gathering_sessions
   WHERE NOT (recruitment_opens_at <= starts_at - interval '2 hours'
              AND recruitment_closes_at < starts_at
              AND starts_at < ends_at);
  IF bad > 0 THEN
    RAISE EXCEPTION 'schedule constraint violated in % rows', bad;
  END IF;
END $$;

-- 확인했으면 COMMIT, 아니면 ROLLBACK
COMMIT;
