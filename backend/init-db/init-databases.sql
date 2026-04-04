\set ON_ERROR_STOP on

SELECT 'CREATE DATABASE auth_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db');
\gexec

SELECT 'CREATE DATABASE budget_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'budget_db');
\gexec

SELECT 'CREATE DATABASE fasting_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fasting_db');
\gexec
