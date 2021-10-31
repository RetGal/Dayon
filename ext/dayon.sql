BEGIN TRANSACTION;
CREATE TABLE IF NOT EXISTS `tokens` (
	`token`	TEXT,
	`assistant`	TEXT,
	`port`	INTEGER,
	`assisted`	TEXT,
	`ts`	INTEGER,
	PRIMARY KEY(`token`)
);
COMMIT;
