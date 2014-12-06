package ch.carteggio.net;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailUtils {

	private static final Pattern[] mQuotePatterns = {		
			Pattern.compile("(> )?On [^\\n]+ wrote:\\n+(>[^\\n]*\\n)+", Pattern.MULTILINE),
			Pattern.compile("(>[^\\n]*\\n){2,}", Pattern.MULTILINE),			
	};
	
	public static boolean containsQuote(String text) {

		String normalizedText = text.replace("\r\n", "\n");
		
		for ( Pattern pattern : mQuotePatterns ) {
			Matcher matcher = pattern.matcher(normalizedText);
			if ( matcher.find() ) return true;
		}
		
		return false;
		
	}
	
	public static String stripQuote(String text) {
		
		String normalizedText = text.replace("\r\n", "\n");
		
		for ( Pattern pattern : mQuotePatterns ) {
			Matcher matcher = pattern.matcher(normalizedText);
			normalizedText = matcher.replaceAll("");
		}
		
		return normalizedText.trim();
		
	}

}
