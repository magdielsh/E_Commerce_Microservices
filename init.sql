-- ═══════════════════════════════════════════════════════════════════════════
-- SCRIPT DE INICIALIZACIÓN DE BASE DE DATOS
-- Se ejecuta UNA SOLA VEZ cuando se crea el contenedor PostgreSQL.
-- Las migraciones de schema las gestiona Flyway (src/main/resources/db/migration/)
-- ═══════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────
-- USUARIO EXCLUSIVO PARA DEBEZIUM
--
-- Principio de mínimo privilegio: Debezium solo necesita:
--   1. REPLICATION → leer el WAL (Write-Ahead Log)
--   2. SELECT → leer el snapshot inicial de las tablas
--   3. USAGE en el schema → ver la estructura
--
-- NUNCA uses el usuario de aplicación (app_user) para Debezium.
-- Si el conector se compromete, el atacante solo tiene acceso de lectura.
-- ─────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'debezium_user') THEN
        CREATE USER debezium_user WITH PASSWORD 'debezium_secure_pass_2024' REPLICATION LOGIN;
    END IF;
END
$$;

-- Permisos sobre la base de datos
GRANT CONNECT ON DATABASE mensajes_db TO debezium_user;

-- Permiso para ver la estructura del schema público
GRANT USAGE ON SCHEMA public TO debezium_user;

-- Permiso de lectura en TODAS las tablas presentes y FUTURAS del schema.
-- ALTER DEFAULT PRIVILEGES asegura que las tablas creadas por Flyway
-- también sean visibles para Debezium automáticamente.
GRANT SELECT ON ALL TABLES IN SCHEMA public TO debezium_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO debezium_user;
