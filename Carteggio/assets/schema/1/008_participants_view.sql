CREATE VIEW view_participants AS
	SELECT 	contacts.email				AS email,
			contacts.name				AS name,
			contacts.contact_id			AS contact_id,
			contacts.color				AS color,
			part._id 					AS _id,
			part.conversation_id		AS conversation_id
	FROM	participants AS part
	INNER JOIN contacts ON part.contact_id = contacts._id;
