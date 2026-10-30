-- horário e telefone vindos do OpenStreetMap (opening_hours e phone)
-- rode depois de farmacias_reais.sql e ubs.sql (pode rodar mais de uma vez)

BEGIN;

ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS horario_funcionamento VARCHAR(300);
ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS telefone VARCHAR(100);
ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS fonte_horario VARCHAR(40);
ALTER TABLE ubs ADD COLUMN IF NOT EXISTS horario_funcionamento VARCHAR(300);
ALTER TABLE ubs ADD COLUMN IF NOT EXISTS telefone VARCHAR(100);
ALTER TABLE ubs ADD COLUMN IF NOT EXISTS fonte_horario VARCHAR(40);

UPDATE farmacia SET horario_funcionamento = NULL, telefone = NULL, fonte_horario = NULL WHERE cnpj IS NOT NULL;
UPDATE ubs SET horario_funcionamento = NULL, telefone = NULL, fonte_horario = NULL;

UPDATE farmacia SET horario_funcionamento = 'Mo-Su 07:00-23:00; PH off', telefone = '+55 15 3239-7270', fonte_horario = 'OpenStreetMap' WHERE cnpj = '71448047002023';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = '+55 15 99818-2109', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865025901';
UPDATE farmacia SET horario_funcionamento = 'PH,Mo-Su 07:00-23:00', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865035290';
UPDATE farmacia SET horario_funcionamento = 'Mo-Su 07:00-23:00', telefone = '+55 15 3234-8069', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61412110022710';
UPDATE farmacia SET horario_funcionamento = 'Mo-Sa 00:00-2359', telefone = '+55 15 3233 2973', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865174907';
UPDATE farmacia SET horario_funcionamento = 'Mo-Fr 09:00-18:00; Sa 08:00-13:00', telefone = '+55 15 3211-2020', fonte_horario = 'OpenStreetMap' WHERE cnpj = '43600988000132';
UPDATE farmacia SET horario_funcionamento = 'Mo-Su 07:00-23:00; PH off', telefone = '+55 15 3037 2201', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61412110068116';
UPDATE farmacia SET horario_funcionamento = 'PH,Mo-Su 07:00-23:00', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865278869';
UPDATE farmacia SET horario_funcionamento = 'Mo-Fr 08:00-20:00; Sa 08:00-19:00', telefone = '+55 15 3231-0533', fonte_horario = 'OpenStreetMap' WHERE cnpj = '00603734000165';
UPDATE farmacia SET horario_funcionamento = 'Mo-Fr 08:00-20:00; Sa 08:00-13:00', telefone = '+55 15 3233-1981', fonte_horario = 'OpenStreetMap' WHERE cnpj = '05662981000156';
UPDATE farmacia SET horario_funcionamento = 'Mo-Su 07:00-23:00', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '71448047002104';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = '+55 15 3202 1355', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61412110090987';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = '+55 15 3233 1455', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865064710';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '06626253112898';
UPDATE farmacia SET horario_funcionamento = 'PH,Mo-Su 07:00-22:00', telefone = '+55 15 3199 4454', fonte_horario = 'OpenStreetMap' WHERE cnpj = '54375647046895';
UPDATE farmacia SET horario_funcionamento = 'Mo-Fr 09:00-19:30; Sa 09:00-18:00', telefone = '+55 15 3233-0100', fonte_horario = 'OpenStreetMap' WHERE cnpj = '03664659000130';
UPDATE farmacia SET horario_funcionamento = 'PH,Mo-Su 07:00-23:00', telefone = '+55 15 3227 4551', fonte_horario = 'OpenStreetMap' WHERE cnpj = '71448047001566';
UPDATE farmacia SET horario_funcionamento = 'Mo-Sa 07:00-23:00; PH off', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '07685223001231';
UPDATE farmacia SET horario_funcionamento = 'PH,Mo-Su 07:00-23:00', telefone = '+55 15 3233 0871', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865044957';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '61412110058749';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = '+55 15 3227 9843', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865256385';
UPDATE farmacia SET horario_funcionamento = NULL, telefone = '+55 15 3218 1323', fonte_horario = NULL WHERE cnpj = '61585865148302';
UPDATE farmacia SET horario_funcionamento = NULL, telefone = NULL, fonte_horario = NULL WHERE cnpj = '27330177000102';
UPDATE farmacia SET horario_funcionamento = 'Mo-Su 07:00-23:00', telefone = '0800 772 2667', fonte_horario = 'OpenStreetMap' WHERE cnpj = '57508426011374';
UPDATE farmacia SET horario_funcionamento = 'Mo-Fr 08:00-19:00; Sa 08:00-13:00', telefone = '+55 15 3211-4114', fonte_horario = 'OpenStreetMap' WHERE cnpj = '10243662000146';
UPDATE farmacia SET horario_funcionamento = 'Mo-Fr 08:00-20:00; Sa 08:00-18:00; Su 08:00-12:00', telefone = '+55 15 3232-9830;+55 15 98138-0138', fonte_horario = 'OpenStreetMap' WHERE cnpj = '05056773000103';
UPDATE farmacia SET horario_funcionamento = 'Mo-Fr 07:00-21:00; Sa 07:00-18:00', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '45399961000310';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = '+55 15 3232-7966', fonte_horario = 'OpenStreetMap' WHERE cnpj = '55450456000579';
UPDATE farmacia SET horario_funcionamento = 'Mo-Sa 07:00-20:00', telefone = '+55 15 3233-4449', fonte_horario = 'OpenStreetMap' WHERE cnpj = '06626253039104';
UPDATE farmacia SET horario_funcionamento = 'Mo-Su 07:00-23:00', telefone = '+55 15 3233-7426;+55 15 99871-0073', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865064206';
UPDATE farmacia SET horario_funcionamento = 'Mo-Su,PH 07:00-23:00', telefone = '+55 15 3233 8943', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865106227';
UPDATE farmacia SET horario_funcionamento = 'Mo-Su 07:00-23:00', telefone = '+55 15 3211-1208;+55 15 3211-0039', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61585865161244';
UPDATE farmacia SET horario_funcionamento = 'PH,Mo-Su 07:00-22:00', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '61412110124806';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = '+55 15 3202 1303;+55 15 3217 6429', fonte_horario = 'OpenStreetMap' WHERE cnpj = '61412110036346';
UPDATE farmacia SET horario_funcionamento = 'Mo-Fr 08:00-20:00; Sa 08:00-13:00', telefone = '+55 15 3226-4105', fonte_horario = 'OpenStreetMap' WHERE cnpj = '03597241000158';
UPDATE farmacia SET horario_funcionamento = 'Mo-Fr 08:00-18:00; Sa 08:00-12:00', telefone = '+55 15 3232-6116;+55 15 98812-4205', fonte_horario = 'OpenStreetMap' WHERE cnpj = '07195232000191';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '52318326000246';
UPDATE farmacia SET horario_funcionamento = '24/7', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE cnpj = '61412110059206';
UPDATE farmacia SET horario_funcionamento = NULL, telefone = '+55 15 3037 6067', fonte_horario = NULL WHERE cnpj = '29299362000107';
UPDATE farmacia SET horario_funcionamento = 'Mo-Sa 08:00-20:00', telefone = '+55 15 3226-5175', fonte_horario = 'OpenStreetMap' WHERE cnpj = '31708168000180';
UPDATE farmacia SET horario_funcionamento = 'Mo-Sa 07:00-22:00; Su 07:00-20:00; PH off', telefone = '+55 15 3243 3118', fonte_horario = 'OpenStreetMap' WHERE cnpj = '71605265011520';
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-17:00; PH off', telefone = '+55 15 3239 5016', fonte_horario = 'OpenStreetMap' WHERE id = 5702593;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-19:00; PH off', telefone = '+55 15 3227 5438', fonte_horario = 'OpenStreetMap' WHERE id = 2034778;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-17:00; PH off', telefone = '+55 15 3232 6520', fonte_horario = 'OpenStreetMap' WHERE id = 2034786;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-17:00; PH off', telefone = '+55 15 3233 1559', fonte_horario = 'OpenStreetMap' WHERE id = 2034794;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-19:00; PH off', telefone = '+55 15 3227-2370', fonte_horario = 'OpenStreetMap' WHERE id = 2035847;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-17:00; PH off', telefone = '+55 15 3221 1214', fonte_horario = 'OpenStreetMap' WHERE id = 2044064;
UPDATE ubs SET horario_funcionamento = NULL, telefone = '+55 15 3226-9092', fonte_horario = NULL WHERE id = 2049244;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-17:00; PH off', telefone = '+55 15 3225 1121', fonte_horario = 'OpenStreetMap' WHERE id = 2049287;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-19:00; PH off', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE id = 2049295;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-17:00', telefone = '+55 15 3234 5620', fonte_horario = 'OpenStreetMap' WHERE id = 2051990;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-19:00; PH off', telefone = '+55 15 3233 7165', fonte_horario = 'OpenStreetMap' WHERE id = 2052032;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-19:00; PH off', telefone = '+55 15 3235 7171', fonte_horario = 'OpenStreetMap' WHERE id = 2055678;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-19:00; PH off', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE id = 2055139;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-19:00; PH off', telefone = '+55 15 3231 4604;+55 15 3223 2228', fonte_horario = 'OpenStreetMap' WHERE id = 2059487;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-17:00; PH off', telefone = '+55 15 3226 1370', fonte_horario = 'OpenStreetMap' WHERE id = 2061031;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-19:00; PH off', telefone = '+55 15 3226 1001', fonte_horario = 'OpenStreetMap' WHERE id = 2070669;
UPDATE ubs SET horario_funcionamento = NULL, telefone = NULL, fonte_horario = NULL WHERE id = 2690853;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-17:00', telefone = '+55 15 3226 4176', fonte_horario = 'OpenStreetMap' WHERE id = 5878128;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 07:00-17:00; PH off', telefone = NULL, fonte_horario = 'OpenStreetMap' WHERE id = 6392989;
UPDATE ubs SET horario_funcionamento = NULL, telefone = '+55 15 3239 9245', fonte_horario = NULL WHERE id = 7114354;
UPDATE ubs SET horario_funcionamento = 'Mo-Fr 08:00-17:00; PH off', telefone = '+55 15 3223 3131', fonte_horario = 'OpenStreetMap' WHERE id = 9132015;

COMMIT;
