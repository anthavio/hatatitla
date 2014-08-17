package net.anthavio.httl.util;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Digest from various apache commons libraries
 * 
 * @author martin.vanek
 *
 */
public class Cutils {

	public static final Charset UTF8 = Charset.forName("utf-8");

	private Cutils() {
		//prevent new instance
	}

	public static boolean isEmpty(String s) {
		return s == null || s.length() == 0;
	}

	public static boolean isNotEmpty(String s) {
		return s != null && s.length() != 0;
	}

	public static boolean isBlank(String s) {
		int strLen;
		if (s == null || (strLen = s.length()) == 0) {
			return true;
		}
		for (int i = 0; i < strLen; i++) {
			if (Character.isWhitespace(s.charAt(i)) == false) {
				return false;
			}
		}
		return true;
	}

	public static boolean isNotBlank(String str) {
		return !isBlank(str);
	}

	public static void close(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Exception x) {
				//System.err.println("xxxxxxxxc " + x);
				//ignore quietly
			}
		}
	}

	public static void close(InputStream i) {
		if (i != null) {
			try {
				i.close();
			} catch (Exception x) {
				//System.err.println("xxxxxxxxi " + x);
				//ignore quietly
			}
		}
	}

	public static void close(OutputStream o) {
		if (o != null) {
			try {
				o.close();
			} catch (Exception x) {
				//ignore quietly
			}
		}
	}

	private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
			'f' };

	public static String md5hex(String input) {
		MessageDigest algo;
		try {
			algo = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException x) {
			throw new IllegalStateException("Cannot create MessageDigest MD5", x);
		}
		byte[] digest = algo.digest(input.getBytes(UTF8));
		return new String(encodeHex(digest));
	}

	protected static char[] encodeHex(byte[] data) {
		int l = data.length;
		char[] out = new char[l << 1];
		// two characters form the hex value.
		for (int i = 0, j = 0; i < l; i++) {
			out[j++] = HEX_DIGITS[(0xF0 & data[i]) >>> 4];
			out[j++] = HEX_DIGITS[0x0F & data[i]];
		}
		return out;
	}
}
