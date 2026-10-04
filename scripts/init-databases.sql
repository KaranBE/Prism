-- Runs once when the Postgres container's data volume is first created.
-- Gives each microservice its own logical database (its own schema, its own migration
-- history) even though they share one Postgres instance in this docker-compose setup.
CREATE DATABASE prism_cache;
CREATE DATABASE prism_usage;
