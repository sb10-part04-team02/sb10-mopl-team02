// DM 조회 여정: 대화 목록 -> 임의 대화의 메시지 목록(커서 순회).
// 대화/메시지도 요청자 기준이라 계정을 VU 별로 분산한다.
// 전제: seed/bulk-seed-social.sql 이 대화(conversation_count)와 메시지(messages_per_conversation)를 심는다.
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/dm-read.js
import { sleep, check } from 'k6';
import http from 'k6/http';
import { BASE_URL, authParams, checkCursorResponse, fetchCursorPages } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { getSession, assertAccountsCoverVus, seedEmail, ACCOUNT_OFFSET, SEED_PASSWORD } from '../lib/accounts.js';
import { optionsWith } from '../config/index.js';

export const options = optionsWith({
  'http_req_duration{name:conversations-list}': ['p(95)<500'],
  'http_req_duration{name:dm-list}': ['p(95)<500'],
});

export function setup() {
  assertAccountsCoverVus();
  const { accessToken } = login(seedEmail(ACCOUNT_OFFSET), SEED_PASSWORD);

  const res = http.get(`${BASE_URL}/api/conversations?limit=1`, authParams(accessToken));
  let hasConversation = false;
  try {
    hasConversation = (res.json('data') || []).length > 0;
  } catch (_) {
    hasConversation = false;
  }
  if (!hasConversation) {
    throw new Error('대화가 없습니다. seed/bulk-seed-social.sql 로 소셜 데이터를 먼저 시딩하세요.');
  }
  return {};
}

export default function () {
  const { accessToken } = getSession();

  // 1) 대화 목록
  const listRes = http.get(
    `${BASE_URL}/api/conversations?sortDirection=DESCENDING&limit=20`,
    authParams(accessToken, { tags: { name: 'conversations-list' } })
  );
  checkCursorResponse(listRes, 'conversations-list');
  // 계약: ConversationDto 는 lastestMessage(서버 필드명 오타 그대로), hasUnread 를 포함한다.
  check(listRes, {
    'conversations-list: ConversationDto shape': (r) => {
      try {
        const data = r.json('data') || [];
        if (data.length === 0) {
          return true;
        }
        const c = data[0];
        return typeof c.hasUnread === 'boolean' && 'lastestMessage' in c;
      } catch (_) {
        return false;
      }
    },
  });

  // 2) 목록에 대화가 있으면 임의 대화의 메시지 목록을 커서 순회
  let conversationIds = [];
  try {
    conversationIds = (listRes.json('data') || []).map((c) => c.id);
  } catch (_) {
    conversationIds = [];
  }
  if (conversationIds.length > 0) {
    const conversationId = conversationIds[Math.floor(Math.random() * conversationIds.length)];
    fetchCursorPages(
      `${BASE_URL}/api/conversations/${conversationId}/direct-messages?sortDirection=DESCENDING&limit=20`,
      accessToken,
      'dm-list'
    );
  }

  sleep(1);
}
