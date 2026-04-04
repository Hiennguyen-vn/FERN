SELECT 'CREATE DATABASE fern_master'
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = 'fern_master'
)\gexec

SELECT 'CREATE DATABASE fern_operational'
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = 'fern_operational'
)\gexec
