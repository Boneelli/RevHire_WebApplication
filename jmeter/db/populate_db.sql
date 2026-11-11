-- =====================================================================
-- populate_db.sql  -  dati di test per i test di carico JMeter (RevHire)
--
-- Genera, in modo deterministico (seed fisso => stessi contenuti ad ogni
-- esecuzione, utile per il confronto before/after):
--   - N_EMPLOYERS  utenti EMPLOYER   + employer_profiles
--   - N_SEEKERS    utenti JOB_SEEKER + job_seeker_profiles + 1 resume ciascuno
--                  (education, experience, skills, certification, project)
--   - N_JOBS       job (circa il 9% CLOSED, gli altri OPEN) + 2-4 job_skills
--   - N_APPLICATIONS candidature uniche (seeker, job) su job OPEN
--   - N_FAVORITES  preferiti unici (seeker, job)
--   - 2 notifiche per utente, una nota ogni 5 candidature
--
-- Credenziali di tutti gli utenti creati:
--   email:    loadtest.emp<k>@revhire.test   (employer, k = 1..N_EMPLOYERS)
--             loadtest.seeker<k>@revhire.test (job seeker, k = 1..N_SEEKERS)
--   password: Test1234!   (salvata gia' come hash BCrypt strength 10,
--                          quindi il login NON fa l'upgrade da testo in chiaro)
--
-- Per cambiare le dimensioni modifica i DEFINE qui sotto.
-- Se i dati loadtest esistono gia', lo script si ferma: esegui prima clean_db.sql
-- =====================================================================

SET ECHO OFF
SET FEEDBACK OFF
SET VERIFY OFF
SET SERVEROUTPUT ON SIZE UNLIMITED
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

DEFINE N_EMPLOYERS    = 5
DEFINE N_SEEKERS      = 30
DEFINE N_JOBS         = 100
DEFINE N_APPLICATIONS = 50
DEFINE N_FAVORITES    = 40

DECLARE
  -- hash BCrypt (strength 10) della password Test1234!
  c_hash   CONSTANT VARCHAR2(100) := '$2b$10$Fo99ZTBuc0kjSwuO0TWnXefpYHwa4tizO0Nozj1yAj2uQv.ywffnG';

  n_emp    CONSTANT PLS_INTEGER := &N_EMPLOYERS;
  n_seek   CONSTANT PLS_INTEGER := &N_SEEKERS;
  n_jobs   CONSTANT PLS_INTEGER := &N_JOBS;
  n_apps   CONSTANT PLS_INTEGER := &N_APPLICATIONS;
  n_favs   CONSTANT PLS_INTEGER := &N_FAVORITES;

  TYPE t_str IS VARRAY(20) OF VARCHAR2(100);
  TYPE t_ids IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
  TYPE t_set IS TABLE OF PLS_INTEGER INDEX BY VARCHAR2(40);

  c_titles     t_str := t_str('Java Developer', 'Data Analyst', 'Project Manager', 'QA Engineer',
                              'DevOps Engineer', 'Frontend Developer', 'Backend Developer',
                              'Data Scientist', 'Product Owner', 'System Administrator');
  c_levels     t_str := t_str('Junior', 'Mid', 'Senior');
  c_cities     t_str := t_str('Roma', 'Milano', 'Napoli', 'Torino', 'Salerno', 'Bologna');
  c_jtypes     t_str := t_str('Full-time', 'Part-time', 'Internship', 'Contract');
  c_edu_req    t_str := t_str('Diploma', 'Laurea Triennale', 'Laurea Magistrale');
  c_skills     t_str := t_str('Java', 'SQL', 'Spring Boot', 'Python', 'Docker', 'Git',
                              'JavaScript', 'Kubernetes', 'Oracle', 'Testing');
  c_industries t_str := t_str('IT', 'Finanza', 'Sanita', 'Logistica', 'Telecomunicazioni');
  c_first      t_str := t_str('Marco', 'Giulia', 'Luca', 'Sara', 'Andrea', 'Chiara',
                              'Matteo', 'Elena', 'Davide', 'Martina');
  c_last       t_str := t_str('Rossi', 'Bianchi', 'Esposito', 'Romano', 'Colombo', 'Ricci',
                              'Marino', 'Greco', 'Bruno', 'Gallo');
  c_degrees    t_str := t_str('Laurea in Informatica', 'Laurea in Ingegneria Informatica',
                              'Laurea Magistrale in Informatica', 'Diploma Tecnico');
  c_univ       t_str := t_str('Universita di Salerno', 'Universita di Napoli',
                              'Politecnico di Milano', 'Universita di Bologna');
  c_emp_status t_str := t_str('Employed', 'Unemployed', 'Student');

  emp_id    t_ids;
  emp_uid   t_ids;
  seek_id   t_ids;
  seek_uid  t_ids;
  res_id    t_ids;
  job_id    t_ids;
  job_emp   t_ids;
  open_idx  t_ids;
  used      t_set;

  n_open    PLS_INTEGER := 0;
  v_uid     NUMBER;
  v_id      NUMBER;
  v_rid     NUMBER;
  v_aid     NUMBER;
  v_cnt     PLS_INTEGER;
  v_n       PLS_INTEGER;
  v_off     PLS_INTEGER;
  v_e       PLS_INTEGER;
  v_s       PLS_INTEGER;
  v_j       PLS_INTEGER;
  v_r       PLS_INTEGER;
  v_made    PLS_INTEGER;
  v_tries   PLS_INTEGER;
  v_days    PLS_INTEGER;
  v_active  NUMBER;
  v_sal_min NUMBER;
  v_sal_max NUMBER;
  v_year    PLS_INTEGER;
  v_key     VARCHAR2(40);
  v_status  VARCHAR2(30);
  v_a       VARCHAR2(200);
  v_b       VARCHAR2(200);
  v_c       VARCHAR2(200);
  v_d       VARCHAR2(200);

  FUNCTION rnd(p_lo PLS_INTEGER, p_hi PLS_INTEGER) RETURN PLS_INTEGER IS
  BEGIN
    RETURN TRUNC(DBMS_RANDOM.VALUE(p_lo, p_hi + 1));
  END;

  FUNCTION pick(p_arr t_str) RETURN VARCHAR2 IS
  BEGIN
    RETURN p_arr(rnd(1, p_arr.COUNT));
  END;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM users WHERE email LIKE 'loadtest.%@revhire.test';
  IF v_cnt > 0 THEN
    RAISE_APPLICATION_ERROR(-20001,
      'Dati loadtest gia presenti (' || v_cnt || ' utenti): esegui prima clean_db.sql');
  END IF;

  DBMS_RANDOM.SEED(42);

  ---------------------------------------------------------------- employer
  FOR e IN 1 .. n_emp LOOP
    v_a := pick(c_industries);
    v_b := pick(c_cities);
    v_n := rnd(10, 5000);

    INSERT INTO users (email, password_hash, role, security_question, security_answer_hash, created_at)
    VALUES ('loadtest.emp' || e || '@revhire.test', c_hash, 'EMPLOYER',
            'Colore preferito?', 'blu', SYSTIMESTAMP)
    RETURNING user_id INTO v_uid;
    emp_uid(e) := v_uid;

    INSERT INTO employer_profiles (user_id, company_name, industry, company_size, description, website, location)
    VALUES (v_uid, 'Azienda Test ' || e, v_a, v_n,
            'Azienda di test numero ' || e || ' creata per i test di carico.',
            'https://azienda' || e || '.example.com', v_b)
    RETURNING employer_id INTO v_id;
    emp_id(e) := v_id;
  END LOOP;

  ---------------------------------------------------------------- seeker + resume
  FOR s IN 1 .. n_seek LOOP
    v_a := pick(c_first) || ' ' || pick(c_last);
    v_b := pick(c_cities);
    v_c := pick(c_emp_status);
    v_n := rnd(0, 12);
    v_r := rnd(60, 100);

    INSERT INTO users (email, password_hash, role, security_question, security_answer_hash, created_at)
    VALUES ('loadtest.seeker' || s || '@revhire.test', c_hash, 'JOB_SEEKER',
            'Colore preferito?', 'blu', SYSTIMESTAMP)
    RETURNING user_id INTO v_uid;
    seek_uid(s) := v_uid;

    INSERT INTO job_seeker_profiles (user_id, full_name, phone, location,
                                     current_employment_status, total_experience, profile_completion)
    VALUES (v_uid, v_a, '+39 333 ' || LPAD(s, 7, '0'), v_b, v_c, v_n, v_r)
    RETURNING seeker_id INTO v_id;
    seek_id(s) := v_id;

    v_d := 'Profilo ' || pick(c_titles) || ' con ' || v_n || ' anni di esperienza, motivato a crescere.';
    INSERT INTO resumes (seeker_id, objective)
    VALUES (v_id, v_d)
    RETURNING resume_id INTO v_rid;
    res_id(s) := v_rid;

    -- 1-2 titoli di studio
    FOR k IN 1 .. rnd(1, 2) LOOP
      v_a := pick(c_degrees);
      v_b := pick(c_univ);
      v_year := rnd(2005, 2024);
      INSERT INTO resume_education (resume_id, degree, institution, year_of_completion)
      VALUES (v_rid, v_a, v_b, v_year);
    END LOOP;

    -- 1-3 esperienze
    FOR k IN 1 .. rnd(1, 3) LOOP
      v_a := pick(c_titles);
      v_n := rnd(1, 6);
      INSERT INTO resume_experience (resume_id, company_name, role, years, description)
      VALUES (v_rid, 'Azienda Precedente ' || k, v_a, v_n,
              'Attivita svolte come ' || v_a || ' presso Azienda Precedente ' || k || '.');
    END LOOP;

    -- 3-5 skill consecutive (quindi distinte)
    v_off := rnd(1, 10);
    FOR k IN 0 .. rnd(3, 5) - 1 LOOP
      INSERT INTO resume_skills (resume_id, skill_name)
      VALUES (v_rid, c_skills(MOD(v_off + k - 1, 10) + 1));
    END LOOP;

    -- 1 certificazione, 1 progetto
    v_a := pick(c_skills);
    v_year := rnd(2015, 2024);
    INSERT INTO resume_certifications (resume_id, certification_name, company, technologies, issue_year)
    VALUES (v_rid, 'Certificazione ' || v_a, 'Ente Certificatore', v_a, v_year);

    v_b := pick(c_skills);
    INSERT INTO resume_projects (resume_id, project_title, description, technologies, project_link)
    VALUES (v_rid, 'Progetto ' || v_b, 'Progetto di esempio sviluppato con ' || v_b || '.',
            v_b, 'https://github.com/example/progetto-' || s);
  END LOOP;

  ---------------------------------------------------------------- jobs
  FOR i IN 1 .. n_jobs LOOP
    v_e := MOD(i - 1, n_emp) + 1;
    v_a := c_levels(MOD(TRUNC((i - 1) / 10), 3) + 1) || ' ' || c_titles(MOD(i - 1, 10) + 1);
    v_b := pick(c_cities);
    v_c := pick(c_jtypes);
    v_d := pick(c_edu_req);
    v_n := rnd(0, 8);
    v_sal_min := 20000 + rnd(0, 20) * 1000;
    v_sal_max := v_sal_min + rnd(5, 30) * 1000;
    v_days := rnd(0, 90);
    v_r := rnd(1, 5);

    IF MOD(i, 11) = 0 THEN
      v_status := 'CLOSED';
      v_active := 0;
    ELSE
      v_status := 'OPEN';
      v_active := 1;
    END IF;

    INSERT INTO jobs (employer_id, title, description, experience_required, education_required,
                      location, salary_min, salary_max, job_type, openings, status, created_at, active)
    VALUES (emp_id(v_e), v_a,
            'Offerta n. ' || i || ' - ' || v_a || ' a ' || v_b || '. Ambiente dinamico e progetti stimolanti.',
            v_n, v_d, v_b, v_sal_min, v_sal_max, v_c, v_r, v_status,
            SYSTIMESTAMP - NUMTODSINTERVAL(v_days, 'DAY'), v_active)
    RETURNING job_id INTO v_id;
    job_id(i) := v_id;
    job_emp(i) := emp_id(v_e);

    IF v_status = 'OPEN' THEN
      n_open := n_open + 1;
      open_idx(n_open) := i;
    END IF;

    -- 2-4 skill richieste (consecutive, quindi distinte)
    v_off := rnd(1, 10);
    FOR k IN 0 .. rnd(2, 4) - 1 LOOP
      INSERT INTO job_skills (job_id, skill_name)
      VALUES (v_id, c_skills(MOD(v_off + k - 1, 10) + 1));
    END LOOP;
  END LOOP;

  IF n_open = 0 THEN
    RAISE_APPLICATION_ERROR(-20002, 'Nessun job OPEN generato: aumenta N_JOBS');
  END IF;

  ---------------------------------------------------------------- applications
  v_made := 0;
  v_tries := 0;
  WHILE v_made < n_apps AND v_tries < n_apps * 100 LOOP
    v_tries := v_tries + 1;
    v_s := rnd(1, n_seek);
    v_j := open_idx(rnd(1, n_open));
    v_key := v_s || '-' || v_j;
    IF NOT used.EXISTS(v_key) THEN
      used(v_key) := 1;
      v_r := rnd(1, 100);
      IF v_r <= 60 THEN
        v_status := 'APPLIED';
      ELSIF v_r <= 75 THEN
        v_status := 'UNDER_REVIEW';
      ELSIF v_r <= 90 THEN
        v_status := 'SHORTLISTED';
      ELSE
        v_status := 'REJECTED';
      END IF;
      v_days := rnd(0, 60);

      INSERT INTO applications (job_id, seeker_id, resume_id, cover_letter, status, applied_date)
      VALUES (job_id(v_j), seek_id(v_s), res_id(v_s),
              'Gentile recruiter, mi candido con entusiasmo per questa posizione.',
              v_status, SYSTIMESTAMP - NUMTODSINTERVAL(v_days, 'DAY'))
      RETURNING application_id INTO v_aid;
      v_made := v_made + 1;

      IF MOD(v_made, 5) = 0 THEN
        INSERT INTO application_notes (application_id, employer_id, note_text, created_at)
        VALUES (v_aid, job_emp(v_j), 'Nota interna di test sulla candidatura ' || v_aid, SYSTIMESTAMP);
      END IF;
    END IF;
  END LOOP;
  IF v_made < n_apps THEN
    RAISE_APPLICATION_ERROR(-20003,
      'Create solo ' || v_made || ' candidature su ' || n_apps || ': servono piu seeker o job OPEN');
  END IF;

  ---------------------------------------------------------------- favorites
  used.DELETE;
  v_made := 0;
  v_tries := 0;
  WHILE v_made < n_favs AND v_tries < n_favs * 100 LOOP
    v_tries := v_tries + 1;
    v_s := rnd(1, n_seek);
    v_j := open_idx(rnd(1, n_open));
    v_key := v_s || '-' || v_j;
    IF NOT used.EXISTS(v_key) THEN
      used(v_key) := 1;
      INSERT INTO favorite_jobs (seeker_id, job_id) VALUES (seek_id(v_s), job_id(v_j));
      v_made := v_made + 1;
    END IF;
  END LOOP;

  ---------------------------------------------------------------- notifications
  FOR e IN 1 .. n_emp LOOP
    INSERT INTO notifications (user_id, message, is_read)
    VALUES (emp_uid(e), 'Hai ricevuto nuove candidature (notifica di test 1)', 0);
    INSERT INTO notifications (user_id, message, is_read)
    VALUES (emp_uid(e), 'Un tuo annuncio e stato visualizzato (notifica di test 2)', 1);
  END LOOP;
  FOR s IN 1 .. n_seek LOOP
    INSERT INTO notifications (user_id, message, is_read)
    VALUES (seek_uid(s), 'Benvenuto su RevHire (notifica di test 1)', 1);
    INSERT INTO notifications (user_id, message, is_read)
    VALUES (seek_uid(s), 'Lo stato di una tua candidatura e cambiato (notifica di test 2)', 0);
  END LOOP;

  COMMIT;
  DBMS_OUTPUT.PUT_LINE('Popolamento completato.');
END;
/

SET FEEDBACK ON
PROMPT
PROMPT Riepilogo (totali presenti nel DB):
SELECT 'utenti employer'  AS tabella, COUNT(*) AS righe FROM users WHERE email LIKE 'loadtest.emp%'
UNION ALL SELECT 'utenti seeker',      COUNT(*) FROM users WHERE email LIKE 'loadtest.seeker%'
UNION ALL SELECT 'job OPEN',           COUNT(*) FROM jobs WHERE status = 'OPEN'
UNION ALL SELECT 'job CLOSED',         COUNT(*) FROM jobs WHERE status = 'CLOSED'
UNION ALL SELECT 'job_skills',         COUNT(*) FROM job_skills
UNION ALL SELECT 'applications',       COUNT(*) FROM applications
UNION ALL SELECT 'favorite_jobs',      COUNT(*) FROM favorite_jobs
UNION ALL SELECT 'resumes',            COUNT(*) FROM resumes
UNION ALL SELECT 'notifications',      COUNT(*) FROM notifications;

EXIT
