-- Script de inicialización para PostgreSQL en Docker
-- Crea la base de datos independiente para ms-reservas (catalogodb se crea por POSTGRES_DB)
SELECT 'CREATE DATABASE reservasdb'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'reservasdb')\gexec

