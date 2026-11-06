-- =====================================================================
-- export_test_data.sql  -  estrae dal DB i dati per i test JMeter
--
-- Stampa su standard output righe CSV, ognuna con un prefisso che indica il file:
--   S,email,password,userId,seekerId,resumeId   -> seekers.csv   (30 seeker di test)
--   E,email,password,userId                     -> employers.csv (5 employer di test)
--   J,jobId                                     -> jobs.csv      (job OPEN)
--   A,applicationId                             -> applications.csv (candidature esistenti)
--   P,userId,seekerId,resumeId,jobId            -> pairs.csv     (coppie seeker/job NUOVE,
--        cioe' senza candidatura ne' preferito: servono per candidarsi e aggiungere preferiti
--        senza ricevere errori di duplicato)
--
-- Gli id cambiano ad ogni popolamento: riesegui questo script dopo ogni populate_db.sql
-- e dopo ogni serie di test che scrive dati (le coppie usate non sono piu' nuove).
-- L'ordine delle coppie e' pseudo-casuale ma deterministico.
-- =====================================================================

SET HEADING OFF
SET PAGESIZE 0
SET FEEDBACK OFF
SET VERIFY OFF
SET DEFINE OFF
SET TRIMSPOOL ON
SET LINESIZE 300
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

SELECT 'S,' || u.email || ',Test1234!,' || u.user_id || ',' || p.seeker_id || ',' || r.resume_id
FROM users u
JOIN job_seeker_profiles p ON p.user_id = u.user_id
JOIN resumes r ON r.seeker_id = p.seeker_id
WHERE u.email LIKE 'loadtest.seeker%@revhire.test'
ORDER BY u.user_id;

SELECT 'E,' || u.email || ',Test1234!,' || u.user_id
FROM users u
WHERE u.email LIKE 'loadtest.emp%@revhire.test'
ORDER BY u.user_id;

SELECT 'J,' || job_id
FROM jobs
WHERE status = 'OPEN'
ORDER BY job_id;

SELECT 'A,' || application_id
FROM applications
ORDER BY application_id;

SELECT 'P,' || p.user_id || ',' || p.seeker_id || ',' || r.resume_id || ',' || j.job_id
FROM job_seeker_profiles p
JOIN users u ON u.user_id = p.user_id AND u.email LIKE 'loadtest.seeker%@revhire.test'
JOIN resumes r ON r.seeker_id = p.seeker_id
CROSS JOIN jobs j
WHERE j.status = 'OPEN'
  AND NOT EXISTS (SELECT 1 FROM applications a WHERE a.job_id = j.job_id AND a.seeker_id = p.seeker_id)
  AND NOT EXISTS (SELECT 1 FROM favorite_jobs f WHERE f.job_id = j.job_id AND f.seeker_id = p.seeker_id)
ORDER BY MOD(p.seeker_id * 7919 + j.job_id * 104729, 100003), p.seeker_id, j.job_id
FETCH FIRST 3000 ROWS ONLY;

EXIT
