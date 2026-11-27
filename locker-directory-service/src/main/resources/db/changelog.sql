--liquibase formatted sql
--changeset locker:directory-1
CREATE TABLE compartment (id text PRIMARY KEY, site text NOT NULL, size text NOT NULL);
CREATE INDEX compartment_site_size ON compartment(site,size);
INSERT INTO compartment VALUES ('CENTRAL-S1','Central','SMALL'),('CENTRAL-S2','Central','SMALL'),
    ('CENTRAL-L1','Central','LARGE'),('NORTH-L1','North','LARGE');
