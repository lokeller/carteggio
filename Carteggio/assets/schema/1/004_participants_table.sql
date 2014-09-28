CREATE TABLE participants (
	_id						INTEGER PRIMARY KEY AUTOINCREMENT,
	contact_id				INTEGER REFERENCES contacts(_id),
	conversation_id			INTEGER REFERENCES conversations(_id) ON DELETE CASCADE
);
