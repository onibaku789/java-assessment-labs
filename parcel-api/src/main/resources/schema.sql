CREATE TABLE IF NOT EXISTS parcel (
    tracking_id VARCHAR(32) PRIMARY KEY UNIQUE NOT NULL,
    status VARCHAR(32) NOT NULL,
    destination VARCHAR(64) NOT NULL
);
INSERT INTO parcel (tracking_id, status, destination)
SELECT 'P-100', 'IN_TRANSIT', 'York'
WHERE NOT EXISTS (SELECT 1 FROM parcel WHERE tracking_id = 'P-100');
