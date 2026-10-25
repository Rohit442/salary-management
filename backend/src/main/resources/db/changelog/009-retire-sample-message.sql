--liquibase formatted sql

--changeset salary-management:009-retire-sample-message
DROP TABLE sample_message;
