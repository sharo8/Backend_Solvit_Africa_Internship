-- Legacy rows may have NULL active; JPQL/list queries treat unknown active inconsistently.
UPDATE tasks SET active = 1 WHERE active IS NULL;
