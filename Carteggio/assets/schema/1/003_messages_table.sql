CREATE TABLE messages (
	_id						INTEGER PRIMARY KEY AUTOINCREMENT,
	global_id				TEXT UNIQUE NOT NULL,
	conversation_id			INTEGER NOT NULL REFERENCES conversations(_id) ON DELETE CASCADE,
	state 					INTEGER,
	sender_id				INTEGER NOT NULL REFERENCES contacts(_id),
	sent_date				INTEGER NOT NULL,
	text					INTEGER
);
