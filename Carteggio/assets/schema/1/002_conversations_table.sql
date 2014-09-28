CREATE TABLE conversations
(
	_id						INTEGER PRIMARY KEY AUTOINCREMENT,
	subject					TEXT,
	last_message_id			INTEGER,
	participants_count 		INTEGER DEFAULT 0,
	participants_names 		TEXT,
	unread_messages_count	INTEGER DEFAULT 0
);
