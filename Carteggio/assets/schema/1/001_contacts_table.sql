CREATE TABLE contacts
(
	_id						INTEGER PRIMARY KEY AUTOINCREMENT,
	color					INTEGER,
	contact_id				INTEGER,
	email					TEXT NOT NULL UNIQUE,
	name					TEXT
);

