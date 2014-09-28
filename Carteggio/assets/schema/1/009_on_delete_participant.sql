CREATE TRIGGER on_delete_participant AFTER DELETE ON participants
BEGIN
	UPDATE	conversations 
	SET	_id = NEW.conversation_id,
		participants_count =
		( 
			SELECT	COUNT(*) 
			FROM	participants
			WHERE	conversation_id = NEW.conversation_id
		),
		participants_names =
		(
			SELECT	GROUP_CONCAT(contacts.name, ', ')
			FROM	participants
			INNER JOIN contacts ON contacts._id = participants.contact_id
			WHERE	conversation_id = NEW.conversation_id
		)
	WHERE _id = NEW.conversation_id;
END;
