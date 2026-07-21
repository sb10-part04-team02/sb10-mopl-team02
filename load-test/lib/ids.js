// 대용량 더미 데이터(load-test/dummy/*.sql, PR #440)의 결정적 PK를 산술로 재계산한다.
// 더미 PK는 duuid('d00000XX', n) = 'd00000XX-0000-4000-8000-<n의 12자리 hex>' 형태라
// DB 조회 없이 유효한 콘텐츠/플리/유저 id 를 만들 수 있다(dummy/_helpers.sql 의 duuid 와 동일 규칙).
//
// 규모(SCALE=1.0): 유저 1..100000, 콘텐츠 1..20000, 플리 1..30000.
// 축소 적재(SCALE=0.01)했다면 상한이 1/100 이므로, 시나리오에서 그 범위 안에서만 뽑아야 404 가 안 난다.

// duuid('d0000002', 42) === 'd0000002-0000-4000-8000-00000000002a'
export function duuid(prefix, n) {
  return `${prefix}-0000-4000-8000-${n.toString(16).padStart(12, '0')}`;
}

export const CONTENT = (c) => duuid('d0000002', c); // 콘텐츠: 1..20000
export const PLAYLIST = (p) => duuid('d0000005', p); // 플레이리스트: 1..30000
export const USER = (u) => duuid('d0000001', u); // 유저: 1..100000 (관리자는 n=0)

// [min, max] 정수 균등 추출(양끝 포함).
export function randInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1));
}

// 리뷰 조회용 콘텐츠 편중 샘플링.
// 더미 리뷰는 콘텐츠별 Zipf(0.7)라 c 가 작을수록 리뷰가 많다(최다 약 3.2만, 롱테일은 수십 건).
// 트래픽을 head:mid:tail = 20:30:50 으로 섞어, 전부 head 만 때려 버퍼 캐시에 상주하는 착시를 피한다.
// 반환: { contentId, bucket } — bucket 을 태그로 써서 그룹별 p95 를 분리 측정한다.
export function pickReviewContent() {
  const r = Math.random();
  let c;
  let bucket;
  if (r < 0.2) {
    c = randInt(1, 10); // head: 리뷰 수천~3.2만
    bucket = 'head';
  } else if (r < 0.5) {
    c = randInt(100, 2000); // mid: 수백
    bucket = 'mid';
  } else {
    c = randInt(5000, 20000); // tail: 수십
    bucket = 'tail';
  }
  return { contentId: CONTENT(c), bucket };
}
