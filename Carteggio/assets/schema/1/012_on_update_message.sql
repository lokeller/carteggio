CREATE TRIGGER on_update_message AFTER UPDATE ON messages
BEGIN
	UPDATE	conversations
	SET	unread_messages_count =
		(
			SELECT	COUNT(*)
			FROM	messages
			WHERE	conversation_id = NEW.conversation_id AND
				messages.state = 4
		)
	 WHERE	_id = NEW.conversation_id;
END;

