package com.team02.mopl.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

  // 파일을 저장하고 접근 가능한 URL을 반환.
  String store(MultipartFile file);

  // 저장된 파일을 삭제. URL이 비었거나 이 저장소가 관리하는 대상이 아니면 무시
  void delete(String url);
}
