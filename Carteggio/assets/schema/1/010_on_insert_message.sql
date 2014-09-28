CREATE TRIGGER on_insert_new_message AFTER INSERT ON messages
	WHEN	NEW.sent_date =
		( 
			SELECT	MAX(sent_date)
			FROM	messages
			WHERE	conversation_id = NEW.conversation_id
		)
BEGIN
	UPDATE	conversations 
	SET	last_message_id = NEW._id,
		unread_messages_count =
		(
			SELECT	COUNT(*)
			FROM	messages
			WHERE	conversation_id = NEW.conversation_id AND
				messages.state = 4
		)
	WHERE	_id = NEW.conversation_id;
END;

