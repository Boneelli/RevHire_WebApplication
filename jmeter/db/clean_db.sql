-- =====================================================================
-- clean_db.sql  -  svuota i dati applicativi di RevHire (Oracle)
--
-- ATTENZIONE: cancella TUTTI i dati delle tabelle dell'applicazione
-- (job, candidature, curriculum, profili, notifiche, utenti...), non solo
-- quelli creati da populate_db.sql.
-- Gli utenti con ruolo ADMIN vengono conservati.
-- Le tabelle (struttura) restano intatte.
--
-- Nota: i file dei CV caricati sul disco (cartella uploads/) non vengono toccati.
-- =====================================================================

SET ECHO OFF
SET FEEDBACK ON
SET VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

-- prima le tabelle figlie, poi quelle padre
DELETE FROM application_notes;
DELETE FROM applications;
DELETE FROM favorite_jobs;
DELETE FROM job_skills;
DELETE FROM jobs;

DELETE FROM resume_files;
DELETE FROM resume_certifications;
DELETE FROM resume_projects;
DELETE FROM resume_skills;
DELETE FROM resume_experience;
DELETE FROM resume_education;
DELETE FROM resumes;

DELETE FROM notifications;
DELETE FROM job_seeker_profiles;
DELETE FROM employer_profiles;
DELETE FROM users WHERE role <> 'ADMIN';

COMMIT;

PROMPT
PROMPT Righe rimaste:
SELECT 'users' AS tabella, COUNT(*) AS righe FROM users
UNION ALL SELECT 'jobs',         COUNT(*) FROM jobs
UNION ALL SELECT 'applications', COUNT(*) FROM applications
UNION ALL SELECT 'resumes',      COUNT(*) FROM resumes;

EXIT
