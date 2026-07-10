// 플레이리스트 조회 여정: 목록(커서, updatedAt 정렬) 조회 → 상세 조회.
// 응답에 requester-relative subscribedByMe 가 포함된다.
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/playlist-read.js
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL, authParams, checkCursorResponse } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { pickUser } from '../data/users.js';

export { options } from '../config/index.js';

export function setup() {
  const user = pickUser(0);
  const { accessToken } = login(user.email, user.password);
  return { accessToken };
}

export default function (data) {
  const listParams = authParams(data.accessToken, { tags: { name: 'playlists-list' } });
  const listRes = http.get(
    `${BASE_URL}/api/playlists?sortBy=updatedAt&sortDirection=DESCENDING&limit=20`,
    listParams
  );
  checkCursorResponse(listRes, 'playlists-list');
  // 계약상 응답 항목에 requester-relative subscribedByMe 가 포함돼야 한다.
  check(listRes, {
    'playlists-list: has subscribedByMe': (r) => {
      try {
        const data = r.json('data') || [];
        return data.length === 0 || typeof data[0].subscribedByMe === 'boolean';
      } catch (_) {
        return false;
      }
    },
  });

  let ids = [];
  try {
    ids = (listRes.json('data') || []).map((p) => p.id);
  } catch (_) {
    ids = [];
  }
  if (ids.length > 0) {
    const detailParams = authParams(data.accessToken, { tags: { name: 'playlists-detail' } });
    http.get(`${BASE_URL}/api/playlists/${ids[0]}`, detailParams);
  }

  sleep(1);
}
