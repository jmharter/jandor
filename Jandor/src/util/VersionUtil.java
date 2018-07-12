package util;

import session.Contact;
import session.DeckHeader;
import session.Session;
import session.User;

public class VersionUtil {
	
	public static final String VERSION = "2.0";
	
	public static final String MTG_JSON_VERSION = "3.18";

	private VersionUtil() {}
	
	public static void update() {
		// Update out of date entities based on version
		Contact contact = Session.getInstance().getContact();
		if(!LoginUtil.isLoggedIn() || contact == null) {
			return;
		}
		
		User user = LoginUtil.getUser();
		for(DeckHeader header : Session.getInstance().getDeckHeaders()) {
			if(!header.getVersion().startsWith("1.")) {
				continue;
			}
			if(header.getAuthor() != null && header.getAuthor().equals(contact.getJandorEmail())) {
				header.setVersion(VERSION);
				header.setAuthor(user.getEmail());
				header.setAuthorGUID(user.getGUID());
				header.setAuthorUsername(user.getUsername());
				header.save();
			}
		}
		
	}
	
}
