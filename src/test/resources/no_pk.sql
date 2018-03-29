--
-- PostgreSQL database dump
--

-- Dumped from database version 9.4.9
-- Dumped by pg_dump version 10.0

-- Started on 2018-03-24 13:37:20

SET statement_timeout                   = 0;
SET lock_timeout                        = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding                     = 'UTF8';
SET standard_conforming_strings         = ON;
SET check_function_bodies               = FALSE;
SET client_min_messages                 = warning;
SET row_security                        = off;

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';

SET search_path                         = public, pg_catalog;

SET default_tablespace                  = '';

SET default_with_oids                   = FALSE;

CREATE TABLE no_pk_table(
  value          numeric,
  number         integer,
  parameter      text
);

ALTER TABLE no_pk_table OWNER TO postgres;

CREATE TABLE pk_table(
  id             numeric NOT NULL,
  numeric_column numeric,
  text_column    text
);

ALTER TABLE pk_table OWNER TO postgres;

INSERT INTO no_pk_table(value, number, parameter)
VALUES (11234, 1, 'text-1');
INSERT INTO no_pk_table(value, number, parameter)
VALUES (2345567, 2, 'text-2');
INSERT INTO no_pk_table(value, number, parameter)
VALUES (56431, 3, 'text-3');
INSERT INTO no_pk_table(value, number, parameter)
VALUES (12908634, 4, ' text-4');
INSERT INTO no_pk_table(value, number, parameter)
VALUES (9653, 5, 'text-5');
INSERT INTO no_pk_table(value, number, parameter)
VALUES (98751, 6, 'text-6');
INSERT INTO no_pk_table(value, number, parameter)
VALUES (987, 7, 'duplicate');
INSERT INTO no_pk_table(value, number, parameter)
VALUES (987, 7, 'duplicate');

INSERT INTO pk_table(id, numeric_column, text_column)
VALUES (1, 764322, 'text-2');
INSERT INTO pk_table(id, numeric_column, text_column)
VALUES (2, 345672, 'text-2');
INSERT INTO pk_table(id, numeric_column, text_column)
VALUES (3, 736433, 'text-3');
INSERT INTO pk_table(id, numeric_column, text_column)
VALUES (4, 984564, ' text-4');
INSERT INTO pk_table(id, numeric_column, text_column)
VALUES (5, 127645, 'text-5');
INSERT INTO pk_table(id, numeric_column, text_column)
VALUES (6, 98046, 'text-6');

ALTER TABLE ONLY pk_table ADD CONSTRAINT pk_table_pkey PRIMARY KEY(id);

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


