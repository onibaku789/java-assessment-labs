INSERT INTO parcel (tracking_id, status, destination)
SELECT 'P-100', 'IN_TRANSIT', 'York'
WHERE NOT EXISTS (SELECT 1 FROM parcel WHERE tracking_id = 'P-100');
