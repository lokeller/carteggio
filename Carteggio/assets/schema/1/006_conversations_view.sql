CREATE VIEW view_conversations AS
	SELECT	conv._id			AS _id,
			conv.subject				AS subject,
			conv.participants_count		AS participants_count,
			conv.participants_names		AS participants_names,
			conv.unread_messages_count	AS unread_messages_count,
			contacts.email				AS last_message_sender_email,
			contacts.name				AS last_message_sender_name,
			messages._id				AS last_message_id,
			messages.state				AS last_message_state,
			messages.sent_date			AS last_sent_date,
			messages.text				AS last_message_text
	FROM	conversations AS conv 
	LEFT JOIN messages ON messages._id = conv.last_message_id 
	LEFT JOIN contacts ON messages.sender_id = contacts._id;
