CREATE VIEW view_contacts AS 
	SELECT	contacts._id				AS _id,
			contacts.color				AS color,
			contacts.email				AS email,
			contacts.name				AS name,
			contacts.contact_id			AS contact_id
	FROM	contacts;
