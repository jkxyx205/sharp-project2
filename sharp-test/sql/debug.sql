drop table IF EXISTS t_id_card;
drop table IF EXISTS t_user;
drop table IF EXISTS t_pet;
drop table IF EXISTS t_role;
drop table IF EXISTS t_user_role;
drop table IF EXISTS sys_code_description;
drop table IF EXISTS t_complex_model;

truncate table t_user;
truncate table t_id_card;
truncate table t_pet;
truncate table t_user_role;
truncate table t_role;
truncate table sys_code_description;
truncate table t_complex_model;

select * from t_user;
select * from t_id_card;
select * from t_pet;
select * from t_role;
select * from t_user_role;
select * from sys_code_description;
select * from t_complex_model;