INSERT INTO parcel (tracking_id, status, destination)
SELECT 'LOAD-' || n, 'DELIVERED', CASE WHEN n % 1000 = 0 THEN 'Bath' ELSE 'Leeds' END
FROM generate_series(1, 100000) n ON CONFLICT DO NOTHING;
ANALYZE parcel;
EXPLAIN (ANALYZE, BUFFERS) SELECT tracking_id FROM parcel WHERE destination = 'Bath';
