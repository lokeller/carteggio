CREATE VIEW view_messages AS
	SELECT	messages._id 				AS _id,
			contacts._id 				AS sender_id,
			contacts.color 				AS sender_color,
			contacts.email 				AS sender_email,
			contacts.name 				AS sender_name,
			messages.conversation_id 	AS conversation_id,
			messages.state 				AS state,
			messages.global_id 			AS global_id,
			messages.sent_date			AS sent_date,
			messages.text				AS text
	FROM	messages
	INNER JOIN contacts ON messages.sender_id = contacts._id;

