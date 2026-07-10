-- seq는 디바이스 재시작 시 1부터 리셋되는 보조 카운터라 idempotency 기준으로 쓸 수 없다.
-- 같은 5초 window가 다른 seq로 재전송되면 (media_unit_id, event_time, seq) 제약을 통과해
-- 중복 저장될 수 있으므로, seq를 빼고 (media_unit_id, event_time) 2컬럼으로 좁힌다.
-- ix_vision_summary_media_time은 새 UNIQUE 키가 같은 컬럼을 인덱싱하므로 중복 삭제한다.
ALTER TABLE vision_summary_5s
    DROP INDEX uk_vision_summary_media_time_seq,
    DROP INDEX ix_vision_summary_media_time,
    ADD CONSTRAINT uk_vision_summary_media_time UNIQUE (media_unit_id, event_time);
